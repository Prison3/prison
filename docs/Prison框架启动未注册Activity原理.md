# Prison 框架启动未注册 Activity 原理

## 概述

Android 系统要求所有 Activity 必须在 `AndroidManifest.xml` 中静态注册才能启动。Prison 框架通过**代理替换机制**和**消息循环拦截**，实现了启动未在 Manifest 中注册的 Activity。

## 核心原理

Prison 框架使用**两步替换**机制：

1. **替换阶段**：将未注册的原始 Activity 替换为已注册的 ProxyActivity
2. **恢复阶段**：在消息循环层面拦截，恢复原始 Activity 并执行

## 完整流程

### 1. Activity 启动请求

```
应用调用 startActivity(原始Intent)
    ↓
ActivityManagerCommonProxy.StartActivity.hook()
    ↓
虚拟化 PackageManager.resolveActivity()
    ↓
ActivityManagerService.startActivityAms()
    ↓
ActivityStack.startActivityLocked()
```

### 2. 代理替换（ActivityStack）

在 `ActivityStack.startActivityLocked()` 中，会调用 `getStartStubActivityIntentInner()` 生成代理 Intent：

```java
private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                               int userId, ProxyActivityRecord target,
                                               ActivityInfo activityInfo) {
    Intent shadow = new Intent();
    
    // 1. 根据 Activity 主题选择代理 Activity
    boolean windowIsTranslucent = ...; // 从资源中读取主题属性
    if (windowIsTranslucent) {
        shadow.setComponent(new ComponentName(
            PrisonCore.getPackageName(), 
            ProxyManifest.TransparentProxyActivity(vpid)
        ));
    } else {
        shadow.setComponent(new ComponentName(
            PrisonCore.getPackageName(), 
            ProxyManifest.getProxyActivity(vpid)
        ));
    }
    
    // 2. 保存原始 Activity 信息到 Intent Extra
    ProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, 
                                 target.mActivityRecord, target.mUserId);
    return shadow;
}
```

**关键点**：
- 将 ComponentName 替换为 ProxyActivity（已在 Manifest 注册）
- 原始 Intent、ActivityInfo 等信息保存在 Intent Extra 中

### 3. 保存原始信息（ProxyActivityRecord）

```java
public static void saveStub(Intent shadow, Intent target, 
                            ActivityInfo activityInfo, 
                            IBinder activityRecord, int userId) {
    shadow.putExtra("_B_|_user_id_", userId);
    shadow.putExtra("_B_|_activity_info_", activityInfo);
    shadow.putExtra("_B_|_target_", target);  // 原始 Intent
    BundleCompat.putBinder(shadow, "_B_|_activity_record_v_", activityRecord);
}
```

**保存的信息**：
- `_B_|_target_`：原始 Intent（包含原始 Activity 的 ComponentName）
- `_B_|_activity_info_`：原始 ActivityInfo（包含类名、包名等）
- `_B_|_user_id_`：用户 ID
- `_B_|_activity_record_v_`：ActivityRecord Binder

### 4. 启动 ProxyActivity

```java
// ActivityStack.startActivityInNewTaskLocked()
Intent shadow = startActivityProcess(userId, intent, activityInfo, record);
shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
PrisonCore.getContext().startActivity(shadow);  // 启动 ProxyActivity
```

此时系统启动的是 **ProxyActivity**（已在 Manifest 注册），而不是原始 Activity。

### 5. 消息循环拦截（HCallbackProxy）

`HCallbackProxy` Hook 了 `ActivityThread.mH` 的 `Callback`，拦截 Activity 启动消息：

```java
@Override
public boolean handleMessage(@NonNull Message msg) {
    if (BuildCompat.isPie()) {
        // Android 9.0+ 使用 ClientTransaction
        if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION()) {
            if (handleLaunchActivity(msg.obj)) {
                getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                return true;  // 已处理，不继续传递
            }
        }
    } else {
        // Android 8.0 及以下使用 LAUNCH_ACTIVITY
        if (msg.what == BRActivityThreadH.get().LAUNCH_ACTIVITY()) {
            if (handleLaunchActivity(msg.obj)) {
                getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                return true;
            }
        }
    }
    // 其他消息传递给原始 Callback
    if (mOtherCallback != null) {
        return mOtherCallback.handleMessage(msg);
    }
    return false;
}
```

### 6. 恢复原始 Activity（handleLaunchActivity）

```java
private boolean handleLaunchActivity(Object client) {
    // 1. 获取 Intent（此时是代理 Intent）
    Intent intent = ...;  // 从 ClientTransaction 或 ActivityClientRecord 中获取
    
    // 2. 从 Intent Extra 恢复原始信息
    ProxyActivityRecord stubRecord = ProxyActivityRecord.create(intent);
    ActivityInfo activityInfo = stubRecord.mActivityInfo;
    
    if (activityInfo != null) {
        // 3. 检查进程是否已初始化
        if (PActivityThread.getAppConfig() == null) {
            // 进程未初始化，需要重启进程
            PActivityManager.get().restartProcess(...);
            // 使用启动 Intent 替换
            Intent launchIntentForPackage = PPackageManager.get()
                .getLaunchIntentForPackage(activityInfo.packageName, stubRecord.mUserId);
            ProxyActivityRecord.saveStub(intent, launchIntentForPackage, ...);
            // 更新 ClientTransaction/ActivityClientRecord 中的 Intent
            updateIntentInTransaction(intent, activityInfo);
            return true;
        }
        
        // 4. 进程已初始化，直接恢复原始 Intent
        if (BuildCompat.isPie()) {
            LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
            launchActivityItemContext._set_mIntent(stubRecord.mTarget);  // 恢复原始 Intent
            launchActivityItemContext._set_mInfo(activityInfo);          // 恢复原始 ActivityInfo
        } else {
            ActivityThreadActivityClientRecordContext clientRecordContext = ...;
            clientRecordContext._set_intent(stubRecord.mTarget);
            clientRecordContext._set_activityInfo(activityInfo);
        }
        
        // 5. 通知 ActivityManagerService
        int taskId = ...;
        PActivityManager.get().onActivityCreated(taskId, token, stubRecord.mActivityRecord);
    }
    
    return false;  // 继续正常流程
}
```

**关键操作**：
- 从 Intent Extra 中提取原始 Intent（`stubRecord.mTarget`）
- 从 Intent Extra 中提取原始 ActivityInfo
- **替换** ClientTransaction/ActivityClientRecord 中的 Intent 和 ActivityInfo
- 系统继续执行时，实际创建的是原始 Activity，而不是 ProxyActivity

### 7. Activity 创建

系统继续执行正常的 Activity 创建流程：

```
ActivityThread.performLaunchActivity()
    ↓
创建原始 Activity 实例（通过反射）
    ↓
调用原始 Activity.onCreate()
    ↓
原始 Activity 正常执行
```

此时系统创建的是**原始 Activity**（未在 Manifest 注册），而不是 ProxyActivity。

## 关键代码位置

### 1. 代理 Intent 生成

**文件**：`core/src/main/java/com/android/prison/system/am/ActivityStack.java`

```java
// 第 332-364 行
private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                               int userId, ProxyActivityRecord target,
                                               ActivityInfo activityInfo)
```

### 2. 信息保存

**文件**：`core/src/main/java/com/android/prison/proxy/ProxyActivityRecord.java`

```java
// 第 22-27 行
public static void saveStub(Intent shadow, Intent target, 
                            ActivityInfo activityInfo, 
                            IBinder activityRecord, int userId)
```

### 3. 消息循环拦截

**文件**：`core/src/main/java/com/android/prison/tweaks/HCallbackProxy.java`

```java
// 第 71-101 行
@Override
public boolean handleMessage(@NonNull Message msg)

// 第 119-198 行
private boolean handleLaunchActivity(Object client)
```

### 4. ProxyActivity（备用方案）

**文件**：`core/src/main/java/com/android/prison/proxy/ProxyActivity.java`

```java
// 第 17-32 行
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    // 如果 HCallbackProxy Hook 失败，这里作为备用方案
    ProxyActivityRecord record = ProxyActivityRecord.create(getIntent());
    if (record.mTarget != null) {
        record.mTarget.setExtrasClassLoader(...);
        startActivity(record.mTarget);  // 再次启动原始 Activity
    }
}
```

**注意**：根据代码注释，`ProxyActivity.onCreate()` 一般不会执行，因为 `HCallbackProxy` 已经在消息循环层面拦截了。这里只是作为稳定性保障。

## 为什么可以启动未注册的 Activity？

### 1. 系统层面

系统启动的是 **ProxyActivity**（已在 Manifest 注册），所以系统检查通过。

### 2. 运行时层面

在消息循环层面，通过反射替换了：
- `ClientTransaction` 中的 `LaunchActivityItem.mIntent`
- `ActivityClientRecord` 中的 `intent` 和 `activityInfo`

系统在创建 Activity 时，使用的是替换后的 Intent 和 ActivityInfo，所以实际创建的是原始 Activity。

### 3. 类加载

原始 Activity 的类通过 `ClassLoader` 动态加载，不需要在 Manifest 中注册。

## 流程图

```
┌─────────────────────────────────────────────────────────┐
│  应用调用 startActivity(原始Activity)                    │
└──────────────────┬──────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────────┐
│  ActivityStack.getStartStubActivityIntentInner()        │
│  - 替换 ComponentName → ProxyActivity                   │
│  - 保存原始信息到 Intent Extra                           │
└──────────────────┬──────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────────┐
│  系统启动 ProxyActivity（已在 Manifest 注册）            │
└──────────────────┬──────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────────┐
│  HCallbackProxy.handleMessage()                          │
│  - 拦截 EXECUTE_TRANSACTION/LAUNCH_ACTIVITY 消息         │
└──────────────────┬──────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────────┐
│  HCallbackProxy.handleLaunchActivity()                   │
│  - 从 Intent Extra 恢复原始 Intent 和 ActivityInfo       │
│  - 替换 ClientTransaction/ActivityClientRecord          │
└──────────────────┬──────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────────┐
│  ActivityThread.performLaunchActivity()                  │
│  - 使用替换后的 Intent 和 ActivityInfo                   │
│  - 通过反射创建原始 Activity 实例                        │
└──────────────────┬──────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────────┐
│  原始 Activity.onCreate() 执行                           │
│  （未在 Manifest 注册，但已成功启动）                    │
└─────────────────────────────────────────────────────────┘
```

## 关键设计点

### 1. 代理 Activity 池

- 50 个 ProxyActivity（P0-P49）
- 根据进程 ID（vpid）选择不同的代理 Activity
- 避免代理 Activity 冲突

### 2. 透明代理

- 对应用层完全透明
- 应用无需修改代码
- 原始 Activity 正常执行

### 3. 双重保障

- **主要机制**：HCallbackProxy 在消息循环层面拦截
- **备用机制**：ProxyActivity.onCreate() 中恢复（如果 Hook 失败）

### 4. 多版本兼容

- Android 8.0 及以下：使用 `LAUNCH_ACTIVITY` 消息
- Android 9.0+：使用 `EXECUTE_TRANSACTION` 消息
- Android 12+：特殊处理逻辑

## 限制和注意事项

### 1. 必须在虚拟化环境中

- 原始 Activity 必须在虚拟应用的包中
- 需要虚拟化的 PackageManager 解析
- 需要虚拟化的进程管理

### 2. 类必须可加载

- 原始 Activity 的类必须存在于 APK 中
- ClassLoader 必须能加载该类
- 不能是系统类或未安装的类

### 3. 权限检查

- 原始 Activity 的权限检查在虚拟化环境中进行
- 某些系统级权限可能无法绕过

### 4. 生命周期管理

- Activity 生命周期由虚拟化 ActivityStack 管理
- 与系统 ActivityStack 同步

## 总结

Prison 框架通过以下机制实现启动未注册的 Activity：

1. **代理替换**：将未注册的 Activity 替换为已注册的 ProxyActivity
2. **信息保存**：将原始 Activity 信息保存在 Intent Extra 中
3. **消息拦截**：Hook ActivityThread 的消息循环，拦截启动消息
4. **信息恢复**：在消息处理中恢复原始 Intent 和 ActivityInfo
5. **动态创建**：系统通过反射创建原始 Activity 实例

这种设计使得框架能够：
- 启动任何未在 Manifest 中注册的 Activity
- 对应用层完全透明
- 保持与原始系统的兼容性
- 实现多用户隔离

---

## 相关文档

- [Android 四大组件虚拟化工作原理](./Android四大组件虚拟化工作原理.md)
- [ActivityStack 工作原理](./ActivityStack工作原理.md)
- [ActivityManagerService 工作原理](./ActivityManagerService工作原理.md)
