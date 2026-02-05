# Native Hook Internals and Technical Details

## Contents

1. [Overview](#overview)
2. [Native Hook Categories](#native-hook-categories)
3. [JNI Hook Mechanism](#jni-hook-mechanism)
4. [Native Function Hook Mechanism](#native-function-hook-mechanism)
5. [Hook Class Overview](#hook-class-overview)
6. [Hook Initialization Flow](#hook-initialization-flow)
7. [Key Technical Points](#key-technical-points)
8. [Multi-Version Compatibility](#multi-version-compatibility)
9. [Summary](#summary)

---

## Overview

Native Hook is one of the core techniques in Prison. It intercepts and modifies system behavior at
the native layer, enabling:

- **JNI interception**: change Java-to-native call behavior
- **Syscall interception**: file system / network hooks
- **Path redirection**: map virtual paths to real storage
- **Stealth**: hide framework traces from apps

### Why Native Hook Matters

In Android virtualization, native hooks are the foundation for:

1. **File system virtualization**: redirect file operations
2. **UID spoofing**: modify Binder call UID
3. **Class loading control**: hide framework classes
4. **Resource access control**: prevent conflicts and leakage

---

## Native Hook Categories

### 1. JNI Hook

**Definition**: Hook Java Native Interface (JNI) methods to intercept native method calls.

**Characteristics**:
- Targets methods declared `native` in Java classes
- Implemented by modifying the ART `ArtMethod` structure
- Uses `RegisterNatives` to replace method implementations

**Examples**:
- BinderHook: `Binder.getCallingUid()`
- UnixFileSystemHook: file operations in `UnixFileSystem`
- VMClassLoaderHook: `VMClassLoader.findLoadedClass()`
- RuntimeHook: `Runtime.nativeLoad()`
- DexFileHook: `DexFile.openDexFileNative()`

### 2. Native Function Hook

**Definition**: Hook C/C++ functions directly in shared libraries.

**Characteristics**:
- Targets functions inside `.so` libraries
- Uses inline hook by patching function entry
- Implemented with DobbyHook

**Examples**:
- FileSystemHook: `open()`, `open64()`
- ZlibHook: `deflate()`

---

## JNI Hook Mechanism

### Core Idea

JNI Hook modifies ART’s `ArtMethod` to replace the `entry_point_from_jni`:

```
ArtMethod {
    declaring_class
    access_flags
    method_index
    ...
    entry_point_from_jni  // JNI method entry
    ...
}
```

### Hook Flow (high level)

1. Register helper JNI methods
2. Compute `ArtMethod` size and native entry offset
3. Locate target method’s `ArtMethod`
4. Save original entry
5. Replace with new implementation via `RegisterNatives`
6. Handle `FastNative` flags on Android 8.x

### Example Hook Macro

```cpp
#define HOOK_JNI(ret, func, ...) \
  ret (*orig_##func)(__VA_ARGS__); \
  ret new_##func(__VA_ARGS__)
```

Usage:

```cpp
HOOK_JNI(jint, getCallingUid, JNIEnv *env, jobject obj) {
    int orig = orig_getCallingUid(env, obj);
    return NativeCore::getCallingUid(env, orig);
}
```

---

## Native Function Hook Mechanism

Native hooks replace symbol entry points in shared libraries. Prison uses DobbyHook to:
- Resolve target symbols
- Patch function entry
- Route execution to custom handlers

Typical targets include libc file APIs, zlib compression, and platform-specific functions.

---

## Hook Class Overview

Prison groups hooks by subsystem. Examples:

- **File system**: `FileSystemHook`, `UnixFileSystemHook`
- **Binder/UID**: `BinderHook`, attribution/UID utilities
- **Class loading**: `VMClassLoaderHook`, `DexFileHook`
- **Runtime**: `RuntimeHook`, `JniHook`

Each hook typically:
- Resolves target symbol / method
- Saves original pointer
- Registers replacement
- Applies compatibility handling by Android version

---

## Hook Initialization Flow

1. Load native library
2. Initialize `JniHook` (compute ArtMethod offsets)
3. Register and install JNI hooks
4. Install native function hooks (DobbyHook)
5. Initialize compatibility flags by Android version

---

## Key Technical Points

- **ArtMethod offsets** vary by Android version/ABI
- **FastNative** handling is required on Android 8.x
- **Symbol resolution** may differ by OEM builds
- **Path mapping** must be consistent across JNI and libc hooks
- **Stealth**: avoid detectable side effects in app-visible behaviors

---

## Multi-Version Compatibility

Prison maintains compatibility via:
- API-specific offset detection
- Symbol fallback lists
- Separate handling for Android 8/9/10/11/12/13+ differences
- APEX and libart path variations

---

## Summary

Native Hook provides the low-level foundation for Prison’s virtualization:

1. JNI hooks replace native method entries in ART
2. Native hooks intercept libc/system functions
3. Combined hooks enable path redirection, UID spoofing, and stealth
4. Careful compatibility handling ensures stability across Android versions
