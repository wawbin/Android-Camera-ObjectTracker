//
// Created by Mr.Bin on 2021/11/13.
//

#ifndef CAMERATRACKER_LOGHELPER_H
#define CAMERATRACKER_LOGHELPER_H

#include <android/log.h>

#define LOG_TAG "NativeLog"

#define Loge(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#endif //CAMERATRACKER_LOGHELPER_H
