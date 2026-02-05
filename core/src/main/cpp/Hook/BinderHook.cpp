
#include "BinderHook.h"
#include <IO.h>
#include <NativeCore.h>
#include "UnixFileSystemHook.h"
#import "JniHook/JniHook.h"



HOOK_JNI(jint, getCallingUid, JNIEnv *env, jobject obj) {
    int orig = orig_getCallingUid(env, obj);
    return NativeCore::getCallingUid(env, orig);
}


void BinderHook::install(JNIEnv *env) {
    const char *clazz = "android/os/Binder";
    JniHook::HookJniFun(env, clazz, "getCallingUid", "()I", (void *) new_getCallingUid,
                        (void **) (&orig_getCallingUid), true);
}