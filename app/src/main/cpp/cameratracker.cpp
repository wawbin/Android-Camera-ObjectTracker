#include <jni.h>
#include "helper/LogHelper.h"
#include "ServoHelper.h"

ServoHelper *helper = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_wawbin_cameratracker_repository_ServoNativeImpl_startLink(JNIEnv *env, jobject thiz) {
    if (!helper) helper = new ServoHelper();
    return helper->startLink();
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_wawbin_cameratracker_repository_ServoNativeImpl_rotate(JNIEnv *env, jobject thiz,
                                                               jint degrees) {
    return helper ? helper->rotate(degrees) : -1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_wawbin_cameratracker_repository_ServoNativeImpl_rotateTo(JNIEnv *env, jobject thiz,
                                                                 jint degrees) {
    return helper ? helper->rotateTo(degrees) : -1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_wawbin_cameratracker_repository_ServoNativeImpl_getCurrentRotation(JNIEnv *env,
                                                                           jobject thiz) {
    return helper ? helper->getCurrentRotation() : -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_me_wawbin_cameratracker_repository_ServoNativeImpl_shutdown(JNIEnv *env, jobject thiz) {
    delete helper;
    helper = nullptr;
}