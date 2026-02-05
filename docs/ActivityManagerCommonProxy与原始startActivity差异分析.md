# ActivityManagerCommonProxy 与原始 startActivity 差异分析

## 概述

`ActivityManagerCommonProxy.StartActivity` 是 Hook 了 Android 系统 `IActivityManager.startActivity()` 方法的代理实现。它拦截所有应用启动 Activity 的请求，在虚拟化环境中进行特殊处理，然后转发到虚拟化的 ActivityManagerService。

## 原始 startActivity 流程

原始 Android 系统的 `startActivity` 方法（在 `ActivityManagerService` 中）的典型流程：

```
1. 接收 Intent 参数
2. 验证调用者权限
3. 使用系统 PackageManager 解析 Intent
4. 检查目标 Activity 是否存在
5. 验证权限和启动模式
6. 创建 ActivityRecord
7. 启动目标进程（如果需要）
8. 启动目标 Activity
```

**特点**：
- 直接使用系统 PackageManager
- 直接启动真实的 Activity
- 没有虚拟化隔离
- 没有包名替换

## Hook 版本的差异点

### 1. 包名参数替换（第 36 行）

```java
MethodParameterUtils.replaceFirstAppPkg(args);
```

**作用**：将方法参数中的虚拟应用包名替换为宿主应用包名

**原因**：在虚拟化环境中，应用看到的包名是虚拟包名，但系统服务需要宿主包名

**原始行为**：直接使用传入的包名，不做替换

---

### 2. 代理 Intent 检查（第 42-44 行）

```java
if (intent.getParcelableExtra("_B_|_target_") != null) {
    return method.invoke(who, args);
}
```

**作用**：如果 Intent 中已经包含 `_B_|_target_` 标记，说明这是代理 Intent，直接调用原始方法

**原因**：避免对代理 Intent 进行二次处理，防止循环

**原始行为**：没有这个检查，所有 Intent 都走正常流程

---

### 3. 应用安装请求处理（第 45-71 行）

```java
if (ComponentUtils.isRequestInstall(intent)) {
    // 处理安装逻辑
}
```

**作用**：
- 检测是否是应用安装请求（`application/vnd.android.package-archive`）
- 阻止安装 Prison 宿主应用自身（第 48-64 行）
- 如果允许安装，转换 FileProvider URI（第 69 行）

**关键逻辑**：

#### 3.1 阻止安装 Prison 自身
```java
// 检查是否是尝试安装 Prison 应用
if (packageName.equals(hostPackageName)) {
    Logger.w(TAG, "Blocked attempt to install Prison app from within Prison");
    return 0;  // 返回成功但不实际安装
}
```

**原因**：防止虚拟应用内部安装宿主应用，可能导致系统不稳定

**原始行为**：系统会正常处理所有安装请求，没有这个保护

#### 3.2 安装请求转发
```java
if (PrisonCore.get().getSettings().requestInstallPackage(file, userId)) {
    return 0;  // 由虚拟化系统处理
}
```

**原因**：在虚拟化环境中，安装请求需要特殊处理，可能需要用户确认或权限检查

**原始行为**：直接调用系统安装器

---

### 4. Package URI 重定向（第 72-75 行）

```java
String dataString = intent.getDataString();
if (dataString != null && dataString.equals("package:" + PActivityThread.getAppPackageName())) {
    intent.setData(Uri.parse("package:" + PrisonCore.getPackageName()));
}
```

**作用**：将指向虚拟应用包名的 `package:` URI 重定向到宿主应用包名

**原因**：系统设置页面等需要访问应用详情时，需要访问宿主应用而不是虚拟应用

**原始行为**：直接使用 Intent 中的 URI，不做重定向

**示例**：
- 虚拟应用包名：`com.example.app`
- 宿主应用包名：`com.android.prison`
- 转换：`package:com.example.app` → `package:com.android.prison`

---

### 5. 使用虚拟化 PackageManager 解析（第 77-98 行）

```java
ResolveInfo resolveInfo = PPackageManager.get().resolveActivity(
    intent,
    GET_META_DATA,
    StartActivityCompat.getResolvedType(args),
    PActivityThread.getUserId());
```

**作用**：使用虚拟化的 `PPackageManager` 而不是系统 `PackageManager` 来解析 Activity

**关键差异**：

#### 5.1 第一次解析（第 77-81 行）
- 使用虚拟化 PackageManager
- 在虚拟应用的用户空间中查找

#### 5.2 回退机制（第 82-98 行）
如果第一次解析失败：
```java
if (resolveInfo == null) {
    // 如果 Intent 没有指定包名和组件，尝试使用虚拟应用包名
    if (intent.getPackage() == null && intent.getComponent() == null) {
        intent.setPackage(PActivityThread.getAppPackageName());
    }
    // 再次尝试解析
    resolveInfo = PPackageManager.get().resolveActivity(...);
    // 如果还是失败，恢复原始包名并调用原始方法
    if (resolveInfo == null) {
        intent.setPackage(origPackage);
        return method.invoke(who, args);
    }
}
```

**原因**：
- 虚拟化环境需要在自己的包管理空间中查找组件
- 支持隐式 Intent（没有指定组件）的解析
- 如果虚拟环境中找不到，回退到系统处理

**原始行为**：
- 直接使用系统 `PackageManager.resolveActivity()`
- 在系统全局包管理空间中查找
- 没有用户空间隔离

---

### 6. 设置 ComponentName（第 101-102 行）

```java
intent.setExtrasClassLoader(who.getClass().getClassLoader());
intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
```

**作用**：
- 设置正确的 ClassLoader（确保能加载虚拟应用的类）
- 将 Intent 的 ComponentName 设置为解析到的 Activity

**原因**：
- 虚拟化环境需要正确的类加载器
- 确保 Intent 指向正确的组件

**原始行为**：
- 系统会自动设置 ClassLoader
- ComponentName 由系统解析设置

---

### 7. 调用虚拟化 ActivityManagerService（第 103-111 行）

```java
PActivityManager.get().startActivityAms(PActivityThread.getUserId(),
    StartActivityCompat.getIntent(args),
    StartActivityCompat.getResolvedType(args),
    StartActivityCompat.getResultTo(args),
    StartActivityCompat.getResultWho(args),
    StartActivityCompat.getRequestCode(args),
    StartActivityCompat.getFlags(args),
    StartActivityCompat.getOptions(args));
return 0;
```

**作用**：调用虚拟化的 `ActivityManagerService.startActivityAms()` 而不是系统方法

**关键差异**：

| 项目 | 原始方法 | Hook 方法 |
|------|---------|----------|
| 调用目标 | `ActivityManagerService.startActivity()` | `PActivityManager.startActivityAms()` |
| 用户空间 | 系统全局 | 虚拟化用户空间（userId） |
| Activity 栈 | 系统 ActivityStack | 虚拟化 ActivityStack |
| 进程管理 | 系统进程管理 | 虚拟化进程管理 |
| 代理替换 | 无 | 会替换为 ProxyActivity |

**原因**：
- 虚拟化环境需要独立的 Activity 栈管理
- 需要实现 Activity 代理替换机制
- 需要用户空间隔离

**原始行为**：
- 直接调用系统 `ActivityManagerService`
- 使用系统全局 Activity 栈
- 直接启动真实 Activity

---

## 完整流程对比

### 原始 startActivity 流程

```
应用调用 startActivity()
    ↓
系统 ActivityManagerService.startActivity()
    ↓
系统 PackageManager.resolveActivity()
    ↓
创建 ActivityRecord
    ↓
启动进程（如果需要）
    ↓
启动真实 Activity
```

### Hook 版本流程

```
应用调用 startActivity()
    ↓
ActivityManagerCommonProxy.StartActivity.hook()
    ↓
替换包名参数
    ↓
检查代理 Intent → 如果是，直接调用原始方法
    ↓
检查安装请求 → 特殊处理
    ↓
Package URI 重定向
    ↓
虚拟化 PackageManager.resolveActivity()
    ↓
设置 ComponentName 和 ClassLoader
    ↓
PActivityManager.startActivityAms()
    ↓
虚拟化 ActivityManagerService.startActivityAms()
    ↓
虚拟化 ActivityStack.startActivityLocked()
    ↓
替换为 ProxyActivity
    ↓
启动 ProxyActivity → 恢复原始 Activity
```

---

## 关键设计点

### 1. 透明代理
Hook 方法对应用层完全透明，应用无需修改代码

### 2. 回退机制
如果虚拟化环境无法处理，回退到系统原始方法

### 3. 安全检查
- 阻止安装宿主应用
- 验证组件存在性
- 权限检查（在其他 Hook 中）

### 4. 包名映射
- 虚拟包名 ↔ 宿主包名
- 自动转换和重定向

### 5. 用户空间隔离
- 每个虚拟应用有独立的 userId
- 独立的 Activity 栈
- 独立的包管理空间

---

## 代码示例对比

### 原始调用示例

```java
// 应用代码
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setData(Uri.parse("package:com.example.app"));
startActivity(intent);

// 系统处理
ActivityManagerService.startActivity(intent) {
    // 直接使用系统 PackageManager
    ResolveInfo info = PackageManager.resolveActivity(intent);
    // 直接启动真实 Activity
    startRealActivity(info.activityInfo);
}
```

### Hook 版本处理示例

```java
// 应用代码（相同）
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setData(Uri.parse("package:com.example.app"));
startActivity(intent);

// Hook 处理
ActivityManagerCommonProxy.StartActivity.hook() {
    // 1. 替换包名参数
    replaceFirstAppPkg(args);
    
    // 2. Package URI 重定向
    if (dataString.equals("package:com.example.app")) {
        intent.setData(Uri.parse("package:com.android.prison"));
    }
    
    // 3. 虚拟化解析
    ResolveInfo info = PPackageManager.resolveActivity(intent, userId);
    
    // 4. 设置 ComponentName
    intent.setComponent(new ComponentName(info.packageName, info.name));
    
    // 5. 调用虚拟化服务
    PActivityManager.startActivityAms(userId, intent, ...);
    
    // 6. 虚拟化服务会替换为 ProxyActivity
    // 7. ProxyActivity 恢复原始 Activity
}
```

---

## 总结

`ActivityManagerCommonProxy.StartActivity` 与原始 `startActivity` 的主要差异：

1. **包名替换**：虚拟包名 ↔ 宿主包名
2. **代理检查**：避免对代理 Intent 二次处理
3. **安装保护**：阻止安装宿主应用
4. **URI 重定向**：Package URI 重定向到宿主应用
5. **虚拟化解析**：使用虚拟化 PackageManager
6. **回退机制**：解析失败时回退到系统处理
7. **虚拟化启动**：调用虚拟化 ActivityManagerService
8. **代理替换**：最终会替换为 ProxyActivity

这些差异使得虚拟化环境能够：
- 透明地拦截所有 Activity 启动请求
- 实现多用户隔离
- 实现 Activity 代理替换
- 保持与原始系统的兼容性

---

## 相关文档

- [Android 四大组件虚拟化工作原理](./Android四大组件虚拟化工作原理.md)
- [ActivityStack 工作原理](./ActivityStack工作原理.md)
- [ActivityManagerService 工作原理](./ActivityManagerService工作原理.md)
