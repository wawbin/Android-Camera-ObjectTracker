//
// Created by Mr.Bin on 2022/3/23.
//
#include "ServoHelper.h"
#include <arpa/inet.h>
#include <unistd.h>
#include "helper/LogHelper.h"

#define TCP_SYNCNT 7

bool ServoHelper::startLink() {
    if (socket_fd > 0) shutdownLink();
    struct sockaddr_in dest{AF_INET, htons(56), inet_addr("192.168.6.1")};
    if ((socket_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)) < 0) return false;

    //read 6秒超时 一般不会这么久
    struct timeval timeout = {6, 0};
    setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));

    //connect 超时
    int syncnt = 1;
    setsockopt(socket_fd, IPPROTO_TCP, TCP_SYNCNT, &syncnt, sizeof(syncnt));

    if (connect(socket_fd, (struct sockaddr *) &dest, sizeof(dest)) == 0) {
        // 自动转到90度
        rotateTo(90);
        return true;
    }
    shutdownLink();
    return false;
}

int ServoHelper::rotate(int degrees) {
    return degrees == 0 ? 0 : rotateTo(currentDegree + degrees);
}

int ServoHelper::rotateTo(int degrees) {
    // 超过最大角度
    if (degrees < 0 || degrees > 180) return -2;

    // 还没连接
    if (socket_fd < 0) return -1;
    Loge("Servo Prepare Rotate To %d", degrees);
    int resultCode = -1;
    if (write(socket_fd, &degrees, sizeof(int)) < 0 ||
        read(socket_fd, &resultCode, sizeof(int)) < 0 || resultCode != 0) {
        shutdownLink();
        return -1;
    }
    Loge("Servo Already Rotate To %d", degrees);
    currentDegree = degrees;
    return 0;
}

int ServoHelper::getCurrentRotation() const {
    return currentDegree;
}

void ServoHelper::shutdownLink() {
    if (socket_fd < 0) return;
    close(socket_fd);
    socket_fd = -1;
}

ServoHelper::~ServoHelper() {
    shutdownLink();
}