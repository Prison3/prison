
#ifndef PRISON2_DEXFILEHOOK_H
#define PRISON2_DEXFILEHOOK_H
#include <jni.h>
class DexFileHook{
public:
    static void install(JNIEnv *env);
    static void setFileReadonly(const char* filePath);
};


#endif //PRISON2_DEXFILEHOOK_H
