# 应用检测 Prison 框架方案与反检测机制

## 目录

1. [概述](#概述)
2. [检测方案分类](#检测方案分类)
3. [文件系统检测](#文件系统检测)
4. [包名和进程检测](#包名和进程检测)
5. [类加载检测](#类加载检测)
6. [堆栈跟踪检测](#堆栈跟踪检测)
7. [系统属性检测](#系统属性检测)
8. [系统服务检测](#系统服务检测)
9. [Prison 反检测机制](#prison-反检测机制)
10. [检测对抗总结](#检测对抗总结)

---

## 概述

应用检测 Prison 框架的主要目的是识别应用是否运行在虚拟化环境中，从而采取相应的安全措施（如拒绝服务、限制功能等）。Prison 框架通过多层反检测机制来隐藏自身的存在。

### 检测与反检测的对抗关系

```
应用检测 ←→ Prison 反检测
    ↓              ↓
识别虚拟环境    隐藏虚拟环境
    ↓              ↓
拒绝服务/限制    正常提供服务
```

---

## 检测方案分类

### 1. 静态检测
- **文件系统检测**：检查特定文件或目录是否存在
- **包名检测**：检查是否安装了虚拟化框架的包
- **类加载检测**：检查是否加载了特定类

### 2. 动态检测
- **进程检测**：检查进程名和包名
- **堆栈跟踪检测**：分析异常堆栈中的类名
- **系统服务检测**：检查系统服务返回的信息

### 3. 运行时检测
- **系统属性检测**：检查系统属性值
- **UID/GID 检测**：检查用户 ID 和组 ID
- **网络检测**：检查网络配置和代理

---

## 文件系统检测

### 检测方案

应用通过检查文件系统来判断虚拟化环境：

#### 1. 检查虚拟化框架目录

```java
// 检查常见的虚拟化框架目录
String[] virtualPaths = {
    "/data/virtual",
    "/data/data/com.lody.virtual",
    "/data/data/com.excelliance.dualaid",
    "/data/data/com.lbe.parallel",
    "/data/data/com.dual.dualspace",
    "/data/data/com.android.prisona",  // Prison 框架
    "/prison",
    "/virtual"
};

for (String path : virtualPaths) {
    File file = new File(path);
    if (file.exists()) {
        // 检测到虚拟化环境
        return true;
    }
}
```

#### 2. 检查 Root 工具文件

```java
// 检查 Root 工具（通常虚拟化环境会隐藏这些）
String[] rootFiles = {
    "/system/xbin/su",
    "/system/bin/su",
    "/sbin/su",
    "/system/app/Superuser.apk",
    "/data/local/xbin/su"
};

for (String path : rootFiles) {
    if (new File(path).exists()) {
        // 可能检测到 Root 环境
    }
}
```

#### 3. 检查模拟器文件

```java
// 检查模拟器特征文件
String[] emulatorFiles = {
    "/dev/qemu_pipe",
    "/dev/goldfish_pipe",
    "/system/lib/libc_malloc_debug_qemu.so",
    "/sys/qemu_trace"
};
```

### Prison 反检测机制

#### 1. Native 层文件系统 Hook

**位置**：`core/src/main/cpp/Utils/AntiDetection.cpp`

```cpp
// Hook 文件访问函数
static int my_access(const char *pathname, int mode) {
    if (pathname && (is_blocked_file(pathname) || is_blocked_package(pathname))) {
        LOGD("[file-hide] access blocked: %s", pathname);
        errno = ENOENT;
        return -1;  // 返回文件不存在
    }
    return orig_access(pathname, mode);
}

static int my_stat(const char *pathname, struct stat *buf) {
    if (pathname && (is_blocked_file(pathname) || is_blocked_package(pathname))) {
        errno = ENOENT;
        return -1;
    }
    return orig_stat(pathname, buf);
}
```

**Hook 的函数**：
- `access()` - 检查文件访问权限
- `stat()` / `lstat()` - 获取文件状态
- `fopen()` - 打开文件
- `open()` - 打开文件描述符
- `readlink()` - 读取符号链接
- `opendir()` - 打开目录

**隐藏的文件/目录**：
- Prison 框架相关：`/data/data/com.android.prisona`, `/prison`, `/virtual`
- 其他虚拟化框架：VirtualApp, DualSpace, Parallel Space 等
- Root 工具：su, Superuser, Magisk 等
- Xposed 框架：XposedBridge.jar, xposed.prop 等

#### 2. 隐藏的包名列表

```cpp
static const char* blocked_packages[] = {
    "com.lody.virtual",
    "io.va.exposed",
    "com.benny.openlauncher",
    "com.android.prisona",  // Prison 框架
    // ... 其他虚拟化框架包名
    nullptr
};
```

---

## 包名和进程检测

### 检测方案

#### 1. 检查已安装的包

```java
// 检查是否安装了虚拟化框架
PackageManager pm = context.getPackageManager();
List<PackageInfo> packages = pm.getInstalledPackages(0);

for (PackageInfo pkg : packages) {
    String packageName = pkg.packageName;
    if (packageName.contains("virtual") ||
        packageName.contains("prison") ||
        packageName.equals("com.lody.virtual") ||
        packageName.equals("com.android.prisona")) {
        // 检测到虚拟化框架
        return true;
    }
}
```

#### 2. 检查当前进程名

```java
// 检查进程名
ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();

for (ActivityManager.RunningAppProcessInfo info : processes) {
    String processName = info.processName;
    if (processName.contains("prison") ||
        processName.contains("virtual") ||
        processName.contains(":p")) {  // Prison 进程名格式：包名:p进程ID
        // 检测到虚拟化进程
        return true;
    }
}
```

#### 3. 检查调用者包名

```java
// 通过 ActivityManager 获取调用者包名
String callingPackage = activityManager.getCallingPackage();
if (callingPackage != null && 
    (callingPackage.contains("prison") || 
     callingPackage.equals("com.android.prisona"))) {
    // 检测到 Prison 框架
    return true;
}
```

### Prison 反检测机制

#### 1. PackageManager Hook

**位置**：`core/src/main/java/com/android/prison/tweaks/IPackageManagerProxy.java`

```java
@ProxyMethod("getInstalledPackages")
public static class GetInstalledPackages extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int flags = MethodParameterUtils.toInt(args[0]);
        // 使用虚拟化的 PackageManager，过滤掉 Prison 框架包
        List<PackageInfo> installedPackages = PPackageManager.get()
            .getInstalledPackages(flags, PActivityThread.getUserId());
        return ParceledListSliceCompat.create(installedPackages);
    }
}

@ProxyMethod("getInstalledApplications")
public static class GetInstalledApplications extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int flags = MethodParameterUtils.toInt(args[0]);
        // 返回虚拟化环境中的应用列表，不包含 Prison 框架
        List<ApplicationInfo> installedApplications = PPackageManager.get()
            .getInstalledApplications(flags, PActivityThread.getUserId());
        return ParceledListSliceCompat.create(installedApplications);
    }
}
```

**作用**：
- 过滤掉 Prison 框架的包名
- 只返回虚拟化环境中的应用列表
- 应用无法通过 `getInstalledPackages()` 检测到 Prison 框架

#### 2. ActivityManager Hook

**位置**：`core/src/main/java/com/android/prison/tweaks/ActivityManagerCommonProxy.java`

```java
@ProxyMethod("getCallingPackage")
public static class getCallingPackage extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        // 返回虚拟应用的包名，而不是 Prison 框架的包名
        return PActivityManager.get().getCallingPackage(
            (IBinder) args[0], 
            PActivityThread.getUserId()
        );
    }
}

@ProxyMethod("getCallingActivity")
public static class getCallingActivity extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        // 返回虚拟应用的 Activity，而不是 ProxyActivity
        return PActivityManager.get().getCallingActivity(
            (IBinder) args[0], 
            PActivityThread.getUserId()
        );
    }
}
```

**作用**：
- 隐藏真实的调用者包名（Prison 框架）
- 返回虚拟应用的包名
- 应用无法通过 `getCallingPackage()` 检测到 Prison 框架

#### 3. 进程名伪装

**位置**：`core/src/main/java/com/android/prison/proxy/ProxyManifest.java`

```java
public static String getProcessName(int bPid) {
    return PrisonCore.getPackageName() + ":p" + bPid;
}
```

**进程名格式**：
- 虚拟应用进程：`宿主包名:p进程ID`
- 例如：`com.android.prisona:p0`, `com.android.prisona:p1`

**检测难度**：
- 进程名包含宿主包名，但格式特殊（`:p数字`）
- 可以通过检查进程名格式来检测，但 Prison 可以通过 Hook 隐藏

---

## 类加载检测

### 检测方案

#### 1. 检查 Xposed 相关类

```java
// 检查是否加载了 Xposed 相关类
try {
    Class.forName("de.robv.android.xposed.XposedBridge");
    // 检测到 Xposed 框架
    return true;
} catch (ClassNotFoundException e) {
    // 未检测到
}

// 检查其他 Hook 框架
String[] hookClasses = {
    "me.weishu.epic.art.EpicNative",
    "me.weishu.exposed.ExposedBridge",
    "com.swift.sandhook.SandHook"
};

for (String className : hookClasses) {
    try {
        Class.forName(className);
        return true;  // 检测到 Hook 框架
    } catch (ClassNotFoundException e) {
        // 继续检查
    }
}
```

#### 2. 检查 Prison 相关类

```java
// 检查 Prison 框架类
String[] prisonClasses = {
    "com.android.prison.core.PrisonCore",
    "com.android.prison.proxy.ProxyActivity",
    "com.android.prison.manager.InjectorManager"
};

for (String className : prisonClasses) {
    try {
        Class.forName(className);
        return true;  // 检测到 Prison 框架
    } catch (ClassNotFoundException e) {
        // 继续检查
    }
}
```

### Prison 反检测机制

#### 1. VMClassLoader Hook

**位置**：`core/src/main/cpp/Hook/VMClassLoaderHook.cpp`

```cpp
HOOK_JNI(jobject, findLoadedClass, JNIEnv *env, jobject obj, 
         jobject class_loader, jstring name) {
    const char * nameC = env->GetStringUTFChars(name, JNI_FALSE);
    
    // 检查是否是可疑的类名
    if (strstr(nameC, "de/robv/android/xposed/") ||
        strstr(nameC, "me/weishu/epic") ||
        strstr(nameC, "me/weishu/exposed") ||
        strstr(nameC, "de.robv.android") ||
        strstr(nameC, "me.weishu.epic") ||
        strstr(nameC, "me.weishu.exposed")) {
        env->ReleaseStringUTFChars(name, nameC);
        return nullptr;  // 返回 null，表示类不存在
    }
    
    jobject result = orig_findLoadedClass(env, obj, class_loader, name);
    env->ReleaseStringUTFChars(name, nameC);
    return result;
}
```

**作用**：
- Hook `VMClassLoader.findLoadedClass()` 方法
- 当应用尝试加载 Xposed 或 Hook 框架相关类时，返回 `null`
- 应用认为这些类不存在，无法检测到框架

**注意**：目前代码中没有隐藏 Prison 相关类，这是一个潜在的检测点。

---

## 堆栈跟踪检测

### 检测方案

#### 1. 分析异常堆栈

```java
// 捕获异常并分析堆栈
try {
    throw new Exception("test");
} catch (Exception e) {
    StackTraceElement[] stack = e.getStackTrace();
    for (StackTraceElement element : stack) {
        String className = element.getClassName();
        if (className.contains("xposed") ||
            className.contains("epic") ||
            className.contains("virtual") ||
            className.contains("prison") ||
            className.contains("hook")) {
            // 检测到虚拟化或 Hook 框架
            return true;
        }
    }
}
```

#### 2. 获取当前线程堆栈

```java
// 获取当前线程的堆栈
StackTraceElement[] stack = Thread.currentThread().getStackTrace();
for (StackTraceElement element : stack) {
    String className = element.getClassName();
    // 检查可疑类名
}
```

### Prison 反检测机制

#### StackTraceFilter

**位置**：`core/src/main/java/com/android/prison/utils/StackTraceFilter.java`

```java
public class StackTraceFilter {
    public static void install() {
        try {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                StackTraceElement[] original = e.getStackTrace();
                e.setStackTrace(filterStackTrace(original));  // 过滤堆栈
            });
        } catch (Throwable ignored) {}
    }

    private static StackTraceElement[] filterStackTrace(StackTraceElement[] stack) {
        return java.util.Arrays.stream(stack)
            .filter(element -> !isSuspicious(element.getClassName()))
            .toArray(StackTraceElement[]::new);
    }

    private static boolean isSuspicious(String className) {
        return className.toLowerCase().contains("xposed") ||
               className.toLowerCase().contains("epic") ||
               className.toLowerCase().contains("virtual") ||
               className.toLowerCase().contains("prison") ||  // 隐藏 Prison 相关类
               className.toLowerCase().contains("hook");
    }
}
```

**作用**：
- 过滤异常堆栈中的可疑类名
- 移除包含 "prison"、"hook"、"xposed" 等关键词的堆栈元素
- 应用无法通过分析堆栈检测到框架

**初始化**：在 `PrisonCore` 构造函数中自动安装

```java
private PrisonCore() {
    try {
        SimpleCrashFix.installSimpleFix();
        StackTraceFilter.install();  // 安装堆栈过滤器
        // ...
    }
}
```

---

## 系统属性检测

### 检测方案

#### 1. 检查系统属性

```java
// 检查系统属性
String[] suspiciousProps = {
    "ro.build.tags",           // 检查是否为 test-keys
    "ro.build.type",          // 检查是否为 user
    "ro.debuggable",          // 检查是否可调试
    "ro.secure"               // 检查是否安全
};

for (String prop : suspiciousProps) {
    String value = SystemProperties.get(prop);
    // 分析属性值
}
```

#### 2. 检查 Build 信息

```java
// 检查 Build 信息
if (Build.TAGS.contains("test-keys")) {
    // 可能是非官方 ROM
}

if (Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug")) {
    // 可能是开发版本
}
```

### Prison 反检测机制

#### 系统属性 Hook（Native 层）

**位置**：`core/src/main/cpp/Utils/AntiDetection.cpp`

```cpp
static int (*orig_system_property_get)(const char *name, char *value) = nullptr;

// 可以 Hook system_property_get 来修改返回值
// 但当前代码中未实现
```

**注意**：当前代码中系统属性 Hook 未完全实现，这是一个潜在的检测点。

---

## 系统服务检测

### 检测方案

#### 1. 检查 ActivityManager 返回的信息

```java
// 检查运行中的应用
ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();

for (ActivityManager.RunningAppProcessInfo info : processes) {
    // 检查进程名、包名等
    if (info.processName.contains("prison")) {
        return true;
    }
}
```

#### 2. 检查 PackageManager 返回的信息

```java
// 检查已安装的包
PackageManager pm = context.getPackageManager();
List<PackageInfo> packages = pm.getInstalledPackages(0);

// 检查是否包含虚拟化框架包
```

#### 3. 检查 ContentProvider

```java
// 检查 ContentProvider Authority
ContentResolver resolver = context.getContentResolver();
Cursor cursor = resolver.query(
    Uri.parse("content://" + authority),
    null, null, null, null
);
// 检查 Authority 是否包含 "prison" 或 "proxy"
```

### Prison 反检测机制

#### 1. ActivityManager Hook

**位置**：`core/src/main/java/com/android/prison/tweaks/IActivityManagerProxy.java`

```java
@ProxyMethod("getRunningAppProcesses")
public static class GetRunningAppProcesses extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        // 返回虚拟化环境中的进程列表，过滤掉 Prison 相关进程
        // 或者返回修改后的进程信息
        return method.invoke(who, args);
    }
}
```

#### 2. PackageManager Hook

如前所述，`IPackageManagerProxy` 会过滤掉 Prison 框架的包。

#### 3. ContentProvider Authority 伪装

**位置**：`core/src/main/java/com/android/prison/proxy/ProxyManifest.java`

```java
public static String getProxyAuthorities(int index) {
    return String.format(Locale.CHINA, 
        "%s.proxy_content_provider_%d", 
        PrisonCore.getPackageName(), 
        index
    );
}
```

**Authority 格式**：`宿主包名.proxy_content_provider_数字`

**检测难度**：
- Authority 包含宿主包名，但格式特殊
- 可以通过检查 Authority 模式来检测

---

## Prison 反检测机制

### 反检测机制总结

#### 1. 文件系统层（Native）

| 机制 | 位置 | 作用 |
|------|------|------|
| 文件访问 Hook | `AntiDetection.cpp` | 隐藏虚拟化框架相关文件 |
| 目录访问 Hook | `AntiDetection.cpp` | 隐藏虚拟化框架相关目录 |
| 包名过滤 | `AntiDetection.cpp` | 隐藏虚拟化框架包名 |

#### 2. Java 层系统服务 Hook

| 机制 | 位置 | 作用 |
|------|------|------|
| PackageManager Hook | `IPackageManagerProxy` | 过滤 Prison 框架包 |
| ActivityManager Hook | `IActivityManagerProxy` | 隐藏调用者信息 |
| 进程信息伪装 | `ProxyManifest` | 伪装进程名格式 |

#### 3. 类加载层（Native）

| 机制 | 位置 | 作用 |
|------|------|------|
| VMClassLoader Hook | `VMClassLoaderHook.cpp` | 隐藏 Xposed 相关类 |
| 类名过滤 | `VMClassLoaderHook.cpp` | 返回 null 表示类不存在 |

#### 4. 运行时层（Java）

| 机制 | 位置 | 作用 |
|------|------|------|
| 堆栈过滤 | `StackTraceFilter` | 过滤异常堆栈中的可疑类名 |
| UID 伪装 | `UIDSpoofingHelper` | 伪装 UID 信息 |

### 反检测初始化流程

```
PrisonCore 构造函数
    ↓
安装反检测机制
    ├─ StackTraceFilter.install()
    ├─ SimpleCrashFix.installSimpleFix()
    ├─ NativeCore.init() → 初始化 Native Hook
    └─ InjectorManager.inject() → 注入系统服务代理
```

---

## 检测对抗总结

### 检测方案 vs 反检测机制

| 检测方案 | 检测方法 | Prison 反检测 | 检测难度 |
|---------|---------|--------------|---------|
| **文件系统检测** | 检查 `/data/data/com.android.prisona` 等 | Native Hook `access/stat/fopen` | ⭐⭐⭐ 中等 |
| **包名检测** | `getInstalledPackages()` | `IPackageManagerProxy` 过滤 | ⭐⭐⭐⭐ 较难 |
| **进程检测** | `getRunningAppProcesses()` | 进程名伪装 + Hook | ⭐⭐⭐ 中等 |
| **类加载检测** | `Class.forName()` | `VMClassLoaderHook` 返回 null | ⭐⭐⭐⭐ 较难 |
| **堆栈跟踪检测** | 分析异常堆栈 | `StackTraceFilter` 过滤 | ⭐⭐⭐⭐⭐ 很难 |
| **调用者检测** | `getCallingPackage()` | `IActivityManagerProxy` 伪装 | ⭐⭐⭐⭐ 较难 |
| **系统属性检测** | `SystemProperties.get()` | 未完全实现 | ⭐⭐ 容易 |
| **ContentProvider 检测** | 检查 Authority | Authority 格式伪装 | ⭐⭐⭐ 中等 |

### 潜在检测点

#### 1. Prison 类名检测（未完全防护）

```java
// 当前 VMClassLoaderHook 只隐藏了 Xposed 相关类
// 但没有隐藏 Prison 相关类
try {
    Class.forName("com.android.prison.core.PrisonCore");
    // 可能检测到 Prison 框架
} catch (ClassNotFoundException e) {
    // 未检测到
}
```

**建议**：在 `VMClassLoaderHook` 中添加 Prison 相关类名的过滤。

#### 2. 系统属性检测（未实现）

当前代码中系统属性 Hook 未完全实现，应用可以通过检查系统属性来检测。

**建议**：实现 `system_property_get` Hook，修改可疑属性值。

#### 3. 进程名格式检测

```java
// 检查进程名格式
String processName = getProcessName();
if (processName.matches(".*:p\\d+")) {
    // 检测到 Prison 进程名格式（包名:p数字）
    return true;
}
```

**建议**：通过 Hook `getRunningAppProcesses()` 进一步伪装进程名。

#### 4. ContentProvider Authority 检测

```java
// 检查 ContentProvider Authority
String[] authorities = getContentProviderAuthorities();
for (String authority : authorities) {
    if (authority.contains("proxy_content_provider")) {
        // 检测到代理 ContentProvider
        return true;
    }
}
```

**建议**：使用更隐蔽的 Authority 命名规则。

### 增强反检测的建议

#### 1. 完善 VMClassLoader Hook

```cpp
// 在 VMClassLoaderHook.cpp 中添加
if (strstr(nameC, "com/android/prison/") ||
    strstr(nameC, "com.android.prison")) {
    env->ReleaseStringUTFChars(name, nameC);
    return nullptr;  // 隐藏 Prison 相关类
}
```

#### 2. 实现系统属性 Hook

```cpp
static int my_system_property_get(const char *name, char *value) {
    // 修改可疑的系统属性值
    if (strcmp(name, "ro.debuggable") == 0) {
        strcpy(value, "0");  // 伪装为不可调试
        return strlen(value);
    }
    return orig_system_property_get(name, value);
}
```

#### 3. 增强进程名伪装

```java
// 在 IActivityManagerProxy 中
@ProxyMethod("getRunningAppProcesses")
public static class GetRunningAppProcesses extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        List<RunningAppProcessInfo> processes = (List) method.invoke(who, args);
        // 过滤或修改进程名
        return processes.stream()
            .filter(info -> !info.processName.contains("prison"))
            .map(info -> {
                // 修改进程名格式
                if (info.processName.matches(".*:p\\d+")) {
                    info.processName = extractOriginalPackageName(info.processName);
                }
                return info;
            })
            .collect(Collectors.toList());
    }
}
```

---

## 总结

### 检测方案分类

1. **文件系统检测** - 检查特定文件/目录
2. **包名检测** - 检查已安装的包
3. **进程检测** - 检查进程名和运行中的应用
4. **类加载检测** - 检查是否加载了特定类
5. **堆栈跟踪检测** - 分析异常堆栈
6. **系统服务检测** - 检查系统服务返回的信息
7. **系统属性检测** - 检查系统属性值

### Prison 反检测机制

1. **Native 层文件系统 Hook** - 隐藏文件和目录
2. **Java 层系统服务 Hook** - 过滤和伪装信息
3. **类加载器 Hook** - 隐藏类加载
4. **堆栈过滤器** - 过滤异常堆栈
5. **进程名伪装** - 伪装进程名格式

### 检测难度评估

- **容易检测**：系统属性（未完全防护）
- **中等难度**：文件系统、进程名格式、ContentProvider Authority
- **较难检测**：包名、类加载、调用者信息
- **很难检测**：堆栈跟踪（已过滤）

### 建议

1. 完善 VMClassLoader Hook，隐藏 Prison 相关类
2. 实现系统属性 Hook，修改可疑属性值
3. 增强进程名伪装，避免格式特征
4. 使用更隐蔽的 ContentProvider Authority 命名

---

## 相关文档

- [Android 四大组件虚拟化工作原理](./Android四大组件虚拟化工作原理.md)
- [HCallbackProxy 工作原理与技术细节](./HCallbackProxy工作原理与技术细节.md)
- [ActivityManagerCommonProxy 与原始 startActivity 差异分析](./ActivityManagerCommonProxy与原始startActivity差异分析.md)
