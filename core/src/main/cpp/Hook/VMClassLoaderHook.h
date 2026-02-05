
#ifndef PRISON_VMCLASSLOADERHOOK_H
#define PRISON_VMCLASSLOADERHOOK_H

#include <jni.h>

class VMClassLoaderHook {
public:
    static void hideXposed();
    static void install(JNIEnv *env);
};


#endif //PRISON_VMCLASSLOADERHOOK_H
