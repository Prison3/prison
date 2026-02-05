
#ifndef PRISON_BINDERHOOK_H
#define PRISON_BINDERHOOK_H
#include <jni.h>
class BinderHook {
public:
    static void install(JNIEnv *env);
};

#endif //PRISON_BINDERHOOK_H
