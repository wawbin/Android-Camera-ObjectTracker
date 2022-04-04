//
// Created by Mr.Bin on 2022/3/23.
//
#ifndef CAMERATRACKER_SERVOHELPER_H
#define CAMERATRACKER_SERVOHELPER_H

class ServoHelper {

public:

    ~ServoHelper();

    bool startLink();

    int rotate(int degrees);

    int rotateTo(int degrees);

    int getCurrentRotation() const;

    void shutdownLink();

private:
    // MG90通电后会自动转到90度
    int currentDegree = 90;
    int socket_fd = -1;
};

#endif //CAMERATRACKER_SERVOHELPER_H
