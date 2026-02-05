# Native Hook 工作原理与技术细节

## 目录

1. [概述](#概述)
2. [Native Hook 分类](#native-hook-分类)
3. [JNI Hook 机制](#jni-hook-机制)
4. [Native Function Hook 机制](#native-function-hook-机制)
5. [Hook 类详解](#hook-类详解)
6. [Hook 初始化流程](#hook-初始化流程)
7. [关键技术点](#关键技术点)
8. [多版本兼容性](#多版本兼容性)
9. [总结](#总结)

---

## 概述

Native Hook 是 Prison 框架的核心技术之一，用于在 Native 层拦截和修改系统行为。通过 Hook 机制，框架可以：

- **拦截 JNI 方法调用**：修改 Java 层调用 Native 方法的行为
- **拦截系统调用**：拦截文件系统、网络等系统调用
- **实现路径重定向**：将虚拟应用的路径重定向到实际存储位置
- **隐藏框架痕迹**：防止应用检测到虚拟化环境

### Hook 的重要性

在 Android 虚拟化框架中，Native Hook 是实现以下功能的基础：

1. **文件系统虚拟化**：拦截文件操作，实现路径重定向
2. **UID 伪装**：拦截 Binder 调用，修改调用者 UID
3. **类加载控制**：拦截类加载过程，隐藏框架类
4. **资源访问控制**：拦截资源加载，防止冲突

---

## Native Hook 分类

Prison 框架中的 Native Hook 主要分为两类：

### 1. JNI Hook

**定义**：Hook Java Native Interface (JNI) 方法，拦截 Java 层调用 Native 方法的请求。

**特点**：
- Hook 的是 Java 类中声明为 `native` 的方法
- 通过修改 `ArtMethod` 结构实现
- 使用 `RegisterNatives` 替换方法实现

**应用场景**：
- BinderHook：Hook `Binder.getCallingUid()`
- UnixFileSystemHook：Hook `UnixFileSystem` 的文件操作方法
- VMClassLoaderHook：Hook `VMClassLoader.findLoadedClass()`
- RuntimeHook：Hook `Runtime.nativeLoad()`
- DexFileHook：Hook `DexFile.openDexFileNative()`

### 2. Native Function Hook

**定义**：Hook 原生 C/C++ 函数，直接拦截系统库函数调用。

**特点**：
- Hook 的是动态库（.so）中的 C/C++ 函数
- 通过修改函数入口地址实现
- 使用 DobbyHook 库进行 Hook

**应用场景**：
- FileSystemHook：Hook `open()`、`open64()` 系统调用
- ZlibHook：Hook `deflate()` 压缩函数

---

## JNI Hook 机制

### 核心原理

JNI Hook 通过修改 Android Runtime (ART) 的 `ArtMethod` 结构来实现。`ArtMethod` 是 ART 中表示方法的结构体，包含了方法的元数据和实现指针。

#### ArtMethod 结构

```
ArtMethod {
    // 方法元数据
    declaring_class
    access_flags
    method_index
    ...
    
    // Native 方法实现指针（关键）
    entry_point_from_jni  // JNI 方法的入口地址
    ...
}
```

### Hook 流程

#### 1. 初始化 JniHook

```cpp
void JniHook::InitJniHook(JNIEnv *env, int api_level) {
    registerNative(env);
    HookEnv.api_level = api_level;
    
    // 计算 ArtMethod 结构大小和偏移
    // 通过两个相邻的 native 方法计算偏移
    void *nativeOffset = GetArtMethod(env, clazz, nativeOffsetId);
    void *nativeOffset2 = GetArtMethod(env, clazz, nativeOffset2Id);
    HookEnv.art_method_size = (size_t) nativeOffset2 - (size_t) nativeOffset;
    
    // 计算 native 方法入口地址的偏移
    HookEnv.art_method_native_offset = ...;
}
```

**关键步骤**：
- 注册辅助 JNI 方法（`nativeOffset`、`setAccessible` 等）
- 通过反射获取 `ArtMethod` 指针
- 计算 `ArtMethod` 结构大小和 `entry_point_from_jni` 偏移

#### 2. Hook JNI 方法

```cpp
void JniHook::HookJniFun(JNIEnv *env, const char *class_name, 
                        const char *method_name, const char *sign,
                        void *new_fun, void **orig_fun, bool is_static) {
    // 1. 查找目标类
    jclass clazz = env->FindClass(class_name);
    
    // 2. 获取方法 ID
    jmethodID method = is_static ? 
        env->GetStaticMethodID(clazz, method_name, sign) :
        env->GetMethodID(clazz, method_name, sign);
    
    // 3. 获取 ArtMethod 指针
    auto artMethod = reinterpret_cast<uintptr_t *>(
        GetArtMethod(env, clazz, method)
    );
    
    // 4. 检查方法是否为 native 方法
    if (!CheckFlags(artMethod)) {
        return; // 不是 native 方法，跳过
    }
    
    // 5. 保存原始函数指针
    *orig_fun = reinterpret_cast<void *>(
        artMethod[HookEnv.art_method_native_offset]
    );
    
    // 6. 注册新的 Native 实现
    JNINativeMethod gMethods[] = {
        {method_name, sign, (void *) new_fun},
    };
    env->RegisterNatives(clazz, gMethods, 1);
    
    // 7. 处理 FastNative 标志（Android 8.0-8.1）
    if (HookEnv.api_level == __ANDROID_API_O__ || 
        HookEnv.api_level == __ANDROID_API_O_MR1__) {
        AddAccessFlag((char *) artMethod, kAccFastNative);
    }
}
```

**关键步骤详解**：

1. **查找目标类**：使用 `FindClass` 获取 Java 类引用
2. **获取方法 ID**：根据方法名和签名获取 `jmethodID`
3. **获取 ArtMethod 指针**：通过反射或直接访问获取 `ArtMethod` 结构
4. **验证 Native 方法**：检查 `kAccNative` 标志
5. **保存原始指针**：从 `ArtMethod` 中读取原始函数指针
6. **注册新实现**：使用 `RegisterNatives` 替换方法实现
7. **处理特殊标志**：Android 8.0-8.1 需要设置 `FastNative` 标志

### HOOK_JNI 宏

为了方便编写 Hook 函数，框架提供了 `HOOK_JNI` 宏：

```cpp
#define HOOK_JNI(ret, func, ...) \
  ret (*orig_##func)(__VA_ARGS__); \
  ret new_##func(__VA_ARGS__)
```

**使用示例**：

```cpp
HOOK_JNI(jint, getCallingUid, JNIEnv *env, jobject obj) {
    int orig = orig_getCallingUid(env, obj);
    return NativeCore::getCallingUid(env, orig);
}
```

**展开后**：

```cpp
jint (*orig_getCallingUid)(JNIEnv *env, jobject obj);
jint new_getCallingUid(JNIEnv *env, jobject obj) {
    int orig = orig_getCallingUid(env, obj);
    return NativeCore::getCallingUid(env, orig);
}
```

---

## Native Function Hook 机制

### 核心原理

Native Function Hook 通过修改函数入口地址来实现。当调用被 Hook 的函数时，会跳转到我们的 Hook 函数，执行完后再调用原始函数。

### Hook 流程

#### 1. 使用 DobbyHook

```cpp
#include "Dobby/dobby.h"

void ZlibHook::install() {
    // 1. 打开动态库
    void* handle = xdl_open("libz.so", XDL_DEFAULT);
    if (!handle) {
        return;
    }
    
    // 2. 查找目标函数
    void* deflate_addr = xdl_sym(handle, "deflate", nullptr);
    if (!deflate_addr) {
        xdl_close(handle);
        return;
    }
    
    // 3. Hook 函数
    if (DobbyHook(deflate_addr, (void*)new_deflate, (void**)&orig_deflate)) {
        ALOGE("Failed to hook deflate function");
    }
    
    xdl_close(handle);
}
```

**关键步骤**：

1. **打开动态库**：使用 `xdl_open` 打开目标库（如 `libz.so`）
2. **查找函数地址**：使用 `xdl_sym` 获取函数符号地址
3. **执行 Hook**：使用 `DobbyHook` 替换函数入口
4. **保存原始指针**：`DobbyHook` 会自动保存原始函数指针

#### 2. Hook 函数实现

```cpp
// 原始函数指针
static int (*orig_deflate)(z_streamp strm, int flush) = nullptr;

// Hook 函数
int new_deflate(z_streamp strm, int flush) {
    // Hook 逻辑
    // ...
    
    // 调用原始函数
    return orig_deflate(strm, flush);
}
```

### DobbyHook 原理

DobbyHook 是一个轻量级的 Hook 库，支持 ARM、ARM64、x86、x86_64 架构。其原理是：

1. **修改函数入口**：在函数开头插入跳转指令（如 `B` 指令）
2. **跳转到 Hook 函数**：跳转到我们提供的 Hook 函数
3. **保存原始代码**：保存被修改的原始指令
4. **提供原始函数调用**：通过保存的原始代码调用原始函数

---

## Hook 类详解

### 1. BinderHook

**作用**：Hook `Binder.getCallingUid()` 方法，实现 UID 伪装。

**实现**：

```cpp
HOOK_JNI(jint, getCallingUid, JNIEnv *env, jobject obj) {
    int orig = orig_getCallingUid(env, obj);
    return NativeCore::getCallingUid(env, orig);
}

void BinderHook::install(JNIEnv *env) {
    const char *clazz = "android/os/Binder";
    JniHook::HookJniFun(env, clazz, "getCallingUid", "()I", 
                        (void *) new_getCallingUid,
                        (void **) (&orig_getCallingUid), true);
}
```

**工作流程**：

```
应用调用 Binder.getCallingUid()
    ↓
BinderHook 拦截调用
    ↓
调用原始方法获取 UID
    ↓
NativeCore.getCallingUid() 进行 UID 伪装
    ↓
返回伪装后的 UID
```

### 2. UnixFileSystemHook

**作用**：Hook `UnixFileSystem` 的文件操作方法，实现路径重定向。

**Hook 的方法**：
- `canonicalize0()` - 规范化路径
- `getBooleanAttributes0()` - 获取文件属性
- `getLastModifiedTime0()` - 获取修改时间
- `setPermission0()` - 设置权限
- `createFileExclusively0()` - 创建文件
- `delete0()` - 删除文件
- `list0()` - 列出文件
- `getSpace0()` - 获取空间信息

**实现示例**：

```cpp
HOOK_JNI(jstring, canonicalize0, JNIEnv *env, jobject obj, jstring path) {
    jstring redirect = IO::redirectPath(env, path);
    return orig_canonicalize0(env, obj, redirect);
}
```

**工作流程**：

```
应用调用 File.getCanonicalPath()
    ↓
UnixFileSystemHook 拦截 canonicalize0()
    ↓
IO::redirectPath() 重定向路径
    ↓
调用原始方法处理重定向后的路径
    ↓
返回重定向后的路径
```

### 3. FileSystemHook

**作用**：Hook 系统调用 `open()` 和 `open64()`，阻止访问有问题的资源文件。

**实现**：

```cpp
int new_open(const char *pathname, int flags, ...) {
    // 检查并阻止有问题的路径
    if (pathname != nullptr) {
        if (strstr(pathname, "resource-cache") || 
            strstr(pathname, "@idmap") || 
            strstr(pathname, ".frro") ||
            strstr(pathname, "systemui") ||
            strstr(pathname, "data@resource-cache@")) {
            errno = ENOENT;
            return -1; // 返回文件不存在
        }
    }
    
    // 调用原始函数
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    return orig_open(pathname, flags, mode);
}

void FileSystemHook::install() {
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    void* open_addr = xdl_sym(handle, "open", nullptr);
    DobbyHook(open_addr, (void*)new_open, (void**)&orig_open);
    // ...
}
```

**阻止的路径**：
- `resource-cache` - 资源缓存目录
- `@idmap` - ID 映射文件
- `.frro` - 资源覆盖文件
- `systemui` - 系统 UI 资源
- `data@resource-cache@` - 数据资源缓存

### 4. VMClassLoaderHook

**作用**：Hook `VMClassLoader.findLoadedClass()`，隐藏 Xposed 相关类的加载。

**实现**：

```cpp
HOOK_JNI(jobject, findLoadedClass, JNIEnv *env, jobject obj, 
         jobject class_loader, jstring name) {
    const char * nameC = env->GetStringUTFChars(name, JNI_FALSE);
    
    // 检查是否是 Xposed 相关类
    if (strstr(nameC, "de/robv/android/xposed/") ||
        strstr(nameC, "me/weishu/epic") ||
        strstr(nameC, "me/weishu/exposed") ||
        strstr(nameC, "de.robv.android") ||
        strstr(nameC, "me.weishu.epic") ||
        strstr(nameC, "me.weishu.exposed")) {
        env->ReleaseStringUTFChars(name, nameC);
        return nullptr; // 返回 null，表示类不存在
    }
    
    jobject result = orig_findLoadedClass(env, obj, class_loader, name);
    env->ReleaseStringUTFChars(name, nameC);
    return result;
}
```

**隐藏的类**：
- `de/robv/android/xposed/*` - Xposed 框架类
- `me/weishu/epic*` - Epic Hook 框架类
- `me/weishu/exposed*` - Exposed Hook 框架类

### 5. RuntimeHook

**作用**：Hook `Runtime.nativeLoad()`，监控 native 库加载。

**实现**：

```cpp
HOOK_JNI(jstring, nativeLoad, JNIEnv *env, jobject obj, 
         jstring name, jobject class_loader) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);
    jstring result = orig_nativeLoad(env, obj, name, class_loader);
    env->ReleaseStringUTFChars(name, nameC);
    return result;
}

void RuntimeHook::install(JNIEnv *env) {
    const char *className = "java/lang/Runtime";
    if (NativeCore::getApiLevel() >= __ANDROID_API_Q__) {
        // Android 10+ 使用新的签名
        JniHook::HookJniFun(env, className, "nativeLoad",
                            "(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",
                            (void *) new_nativeLoad2, ...);
    } else {
        // Android 9 及以下使用旧签名
        JniHook::HookJniFun(env, className, "nativeLoad",
                            "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/String;",
                            (void *) new_nativeLoad, ...);
    }
}
```

**版本兼容性**：
- Android 10+：方法签名包含 `caller` 参数
- Android 9 及以下：方法签名不包含 `caller` 参数

### 6. DexFileHook

**作用**：Hook `DexFile.openDexFileNative()`，对虚拟化环境中的 DEX 文件设置只读权限。

**实现**：

```cpp
HOOK_JNI(jobject, openDexFileNative, JNIEnv *env, jobject obj,
         jstring sourceName, jstring outputName, jint flags,
         jobject loader, jobject elements) {
    const char *sourceNameC = env->GetStringUTFChars(sourceName, JNI_FALSE);
    
    // 检查是否是虚拟化环境的 DEX 文件
    if(strstr(sourceNameC, "/prison/") != nullptr) {
        DexFileHook::setFileReadonly(sourceNameC);
    }
    
    jobject orig = orig_openDexFileNative(env, obj, sourceName, outputName, 
                                         flags, loader, elements);
    env->ReleaseStringUTFChars(sourceName, sourceNameC);
    return orig;
}

void DexFileHook::setFileReadonly(const char* filePath) {
    struct stat fileStat;
    if (stat(filePath, &fileStat) != 0) {
        return;
    }
    // 设置文件为只读（权限 0400）
    chmod(filePath, S_IRUSR);
}
```

**工作流程**：

```
应用加载 DEX 文件
    ↓
DexFileHook 拦截 openDexFileNative()
    ↓
检查路径是否包含 "/prison/"
    ↓
如果是，设置文件为只读
    ↓
调用原始方法打开 DEX 文件
```

### 7. ZlibHook

**作用**：Hook `deflate()` 压缩函数，用于某些特殊场景。

**实现**：

```cpp
int new_deflate(z_streamp strm, int flush) {
    // Hook 逻辑
    // ...
    
    // 调用原始函数
    int result = orig_deflate(strm, flush);
    return result;
}

void ZlibHook::install() {
    void* handle = xdl_open("libz.so", XDL_DEFAULT);
    void* deflate_addr = xdl_sym(handle, "deflate", nullptr);
    DobbyHook(deflate_addr, (void*)new_deflate, (void**)&orig_deflate);
    xdl_close(handle);
}
```

---

## Hook 初始化流程

### 完整初始化流程

```
NativeCore.init(apiLevel)
    ↓
JniHook::InitJniHook(env, apiLevel)
    ├─ 注册辅助 JNI 方法
    ├─ 计算 ArtMethod 结构大小
    └─ 计算 native 方法入口偏移
    ↓
IOCore.setupRedirect(context)
    └─ 设置路径重定向规则
    ↓
NativeCore.installHooks(env, clazz, apiLevel)
    ├─ IO::init(env) - 初始化 I/O 系统
    ├─ UnixFileSystemHook::install(env)
    ├─ FileSystemHook::install()
    ├─ VMClassLoaderHook::install(env)
    ├─ RuntimeHook::install(env)
    ├─ BinderHook::install(env)
    ├─ DexFileHook::install(env)
    └─ ZlibHook::install()
```

### 调用时机

**位置**：`PActivityThread.handleBindApplication()`

```java
NativeCore.init(Build.VERSION.SDK_INT);
IOCore.get().setupRedirect(packageContext);
NativeCore.installHooks(Build.VERSION.SDK_INT);
```

**时机**：虚拟应用进程启动时，在 `Application.onCreate()` 之前。

---

## 关键技术点

### 1. ArtMethod 结构访问

#### 获取 ArtMethod 指针

```cpp
void* GetArtMethod(JNIEnv *env, jclass clazz, jmethodID method) {
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        // Android 10+ 使用反射获取
        jclass methodClass = env->FindClass("java/lang/reflect/Method");
        jmethodID getArtMethod = env->GetMethodID(methodClass, "getArtMethod", "()J");
        jobject methodObj = env->ToReflectedMethod(clazz, method, true);
        return reinterpret_cast<void *>(env->CallLongMethod(methodObj, getArtMethod));
    } else {
        // Android 9 及以下直接转换
        return env->FromReflectedMethod(env->ToReflectedMethod(clazz, method, true));
    }
}
```

#### 计算偏移量

```cpp
// 通过两个相邻的 native 方法计算结构大小
void *nativeOffset = GetArtMethod(env, clazz, nativeOffsetId);
void *nativeOffset2 = GetArtMethod(env, clazz, nativeOffset2Id);
HookEnv.art_method_size = (size_t) nativeOffset2 - (size_t) nativeOffset;

// 通过比较 native 方法和非 native 方法计算偏移
HookEnv.art_method_native_offset = ...;
```

### 2. RegisterNatives 替换

`RegisterNatives` 是 JNI 提供的标准 API，用于注册 Native 方法实现。Hook 时，我们用它来替换已有的 Native 方法实现。

**关键点**：
- 必须在方法已经存在的情况下才能替换
- 替换后，原始实现会被覆盖
- 需要提前保存原始函数指针

### 3. FastNative 标志处理

Android 8.0-8.1 引入了 `FastNative` 标志，用于优化 Native 方法调用。Hook 时需要设置此标志：

```cpp
if (HookEnv.api_level == __ANDROID_API_O__ || 
    HookEnv.api_level == __ANDROID_API_O_MR1__) {
    AddAccessFlag((char *) artMethod, kAccFastNative);
}
```

### 4. 错误处理

#### 方法不存在处理

```cpp
if (!method || env->ExceptionCheck()) {
    env->ExceptionClear();
    ALOGD("get method id fail: %s %s (method may not exist in this Android version)", 
          class_name, method_name);
    return;
}
```

#### RegisterNatives 失败处理

```cpp
if (env->RegisterNatives(clazz, gMethods, 1) < 0) {
    env->ExceptionClear();
    ALOGE("jni hook error. class：%s, method：%s (native method may not exist)", 
          class_name, method_name);
    return;
}
```

### 5. 内存管理

#### JNI 字符串处理

```cpp
const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
// 使用 nameC
env->ReleaseStringUTFChars(name, nameC); // 必须释放
```

#### 本地引用管理

```cpp
jclass clazz = env->FindClass(class_name);
// 使用 clazz
env->DeleteLocalRef(clazz); // 释放本地引用
```

---

## 多版本兼容性

### Android 版本差异

#### 1. ArtMethod 访问方式

| Android 版本 | 访问方式 |
|-------------|---------|
| Android 9 及以下 | `FromReflectedMethod()` 直接返回指针 |
| Android 10+ | 需要通过反射调用 `Method.getArtMethod()` |

#### 2. Runtime.nativeLoad 签名

| Android 版本 | 方法签名 |
|-------------|---------|
| Android 9 及以下 | `nativeLoad(String, ClassLoader)` |
| Android 10+ | `nativeLoad(String, ClassLoader, Class)` |

#### 3. FastNative 标志

| Android 版本 | 是否需要设置 |
|-------------|-------------|
| Android 8.0-8.1 | 是 |
| 其他版本 | 否 |

#### 4. DexFile.openDexFileNative

| Android 版本 | 是否支持 |
|-------------|---------|
| Android 14+ (API 34+) | 是 |
| 其他版本 | 否 |

### 兼容性处理示例

```cpp
void RuntimeHook::install(JNIEnv *env) {
    const char *className = "java/lang/Runtime";
    if (NativeCore::getApiLevel() >= __ANDROID_API_Q__) {
        // Android 10+ 使用新签名
        JniHook::HookJniFun(env, className, "nativeLoad",
                            "(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",
                            (void *) new_nativeLoad2, ...);
    } else {
        // Android 9 及以下使用旧签名
        JniHook::HookJniFun(env, className, "nativeLoad",
                            "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/String;",
                            (void *) new_nativeLoad, ...);
    }
}
```

---

## 总结

### Native Hook 的核心价值

1. **透明拦截**：在不修改应用代码的情况下拦截系统调用
2. **灵活控制**：可以修改参数、返回值，甚至完全替换实现
3. **性能优化**：Native 层 Hook 性能开销小
4. **兼容性好**：通过版本检测适配不同 Android 版本

### 技术要点总结

1. **JNI Hook**：
   - 通过修改 `ArtMethod` 结构实现
   - 使用 `RegisterNatives` 替换方法实现
   - 需要计算 `ArtMethod` 结构偏移

2. **Native Function Hook**：
   - 通过修改函数入口地址实现
   - 使用 DobbyHook 库进行 Hook
   - 需要动态库符号查找

3. **版本兼容**：
   - 不同 Android 版本的实现差异
   - 需要根据 API 级别选择不同的 Hook 方式
   - 错误处理和降级策略

4. **内存管理**：
   - JNI 字符串和引用的正确释放
   - 避免内存泄漏

### 应用场景

Native Hook 在 Prison 框架中的应用：

1. **文件系统虚拟化**：UnixFileSystemHook、FileSystemHook
2. **UID 伪装**：BinderHook
3. **反检测**：VMClassLoaderHook
4. **资源管理**：DexFileHook
5. **监控和调试**：RuntimeHook

### 注意事项

1. **稳定性**：Hook 失败可能导致应用崩溃，需要完善的错误处理
2. **性能**：Hook 会增加函数调用开销，需要优化
3. **兼容性**：不同 Android 版本的实现差异需要适配
4. **安全性**：Hook 可能被检测，需要隐藏 Hook 痕迹

---

## 相关文档

- [Android 四大组件虚拟化工作原理](./Android四大组件虚拟化工作原理.md)
- [HCallbackProxy 工作原理与技术细节](./HCallbackProxy工作原理与技术细节.md)
- [应用检测 Prison 框架方案与反检测机制](./应用检测Prison框架方案与反检测机制.md)
