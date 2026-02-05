
#ifndef VIRTUALM_UNIXFILESYSTEMHOOK_H
#define VIRTUALM_UNIXFILESYSTEMHOOK_H
#include <jni.h>
class UnixFileSystemHook {
public:
    static void install(JNIEnv *env);
};


#endif //VIRTUALM_UNIXFILESYSTEMHOOK_H
