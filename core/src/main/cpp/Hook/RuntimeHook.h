
#ifndef PRISON_RUNTIMEHOOK_H
#define PRISON_RUNTIMEHOOK_H

#include <jni.h>

class RuntimeHook {
public:
    static void install(JNIEnv *env);
};


#endif //PRISON_RUNTIMEHOOK_H
