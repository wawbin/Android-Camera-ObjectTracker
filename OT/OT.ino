#include <ESP8266WiFi.h>
#include <Servo.h>

IPAddress apIP(192, 168, 6, 1);
WiFiServer wifiServer(56);
Servo myservo;
#define WIFISSID "tracker"
#define PASSWD "tracker123"
// 这个数和MainActivityVM::RotateModulus一样
#define Rotate_Modulus 15
// 每次转1度 不要转太快 不然容易跟丢
// 必须是Rotate_Modulus的因数
#define EACH_DEGREE 1
// 初始转角 MG90 只能180 得确保从中间开始
#define INITIAL_DEGREE 90
unsigned int currentRotation = INITIAL_DEGREE;

void setup() {
  Serial.begin(115200);
  pinMode(2, OUTPUT);
  myservo.attach(2);
  myservo.write(INITIAL_DEGREE);
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(apIP, apIP, IPAddress(255, 255, 255, 0));
  WiFi.softAP(WIFISSID, PASSWD, 1, 0);
  wifiServer.begin();
}

void loop() {
  if (wifiServer.hasClient()) {
    WiFiClient client = wifiServer.available();
    Serial.println("Client available");
    while (client.connected()) {
      delay(300);
      int rotation = 0;
      if (client.read(reinterpret_cast<uint8_t *>(&rotation), sizeof(int)) <= 0)
        continue;
      if (rotation != currentRotation) {
        Serial.println("Begin Rotate To " + String(rotation));
        // Rotate_Modulus角度才是在寻找 得慢慢转
        if (abs(rotation - currentRotation) == Rotate_Modulus) {
          int direction = rotation < currentRotation ? -1 : 1;
          // rotation+direction*EACHDEGREE 让最后一次旋转可以在FOR里面
          for (int i = currentRotation; i != rotation + direction * EACH_DEGREE;
               i += direction * EACH_DEGREE) {
            myservo.write(i);
            delay(50);
          }
        } else
          myservo.write(rotation);
        currentRotation = rotation;
      }
      Serial.println("Already Rotate To " + String(rotation));
      // 返回0表示完成操作
      rotation = 0;
      client.write(reinterpret_cast<uint8_t *>(&rotation), sizeof(int));
    }
    Serial.println("Client disconnected");
    client.stop();
    // 转回90度
    myservo.write(INITIAL_DEGREE);
    currentRotation = INITIAL_DEGREE;
  }
  delay(300);
}