# ActivityManagerService 工作原理文档

## 目录
1. [概述](#概述)
2. [核心架构](#核心架构)
3. [多用户隔离机制](#多用户隔离机制)
4. [主要功能模块](#主要功能模块)
5. [工作流程](#工作流程)
6. [关键特性](#关键特性)
7. [类关系图](#类关系图)

---

## 概述

`ActivityManagerService` 是虚拟化环境中的核心系统服务，负责管理多用户环境下的 Activity、Service 和 Broadcast。它通过用户空间隔离机制，为每个用户提供独立的组件管理环境。

### 主要职责
- 管理 Activity 的启动和生命周期
- 管理 Service 的启动、绑定和生命周期
- 管理 Broadcast 的发送和接收
- 管理 ContentProvider 的获取
- 提供进程初始化接口
- 管理 IntentSender 和 PendingIntent

---

## 核心架构

### 类结构

```java
public class ActivityManagerService extends IPActivityManagerService.Stub 
                                   implements ISystemService {
    private static final ActivityManagerService sService = new ActivityManagerService();
    private final Map<Integer, UserSpace> mUserSpace = new HashMap<>();
    private final PackageManagerService mPms;
    private final BroadcastManager mBroadcastManager;
}
```

### 关键组件

1. **UserSpace**: 用户空间，为每个用户提供独立的组件管理
2. **ActivityStack**: Activity 栈管理器
3. **ActiveServices**: Service 管理器
4. **BroadcastManager**: Broadcast 管理器
5. **ProcessManagerService**: 进程管理器

---

## 多用户隔离机制

### UserSpace 结构

```java
public class UserSpace {
    public final ActiveServices mActiveServices = new ActiveServices();
    public final ActivityStack mStack = new ActivityStack();
    public final Map<IBinder, PendingIntentRecord> mIntentSenderRecords = new HashMap<>();
}
```

**组成**：
- `mActiveServices`: 管理该用户的 Service
- `mStack`: 管理该用户的 Activity 栈
- `mIntentSenderRecords`: 管理 PendingIntent 记录

### 用户空间获取

```java
private UserSpace getOrCreateSpaceLocked(int userId) {
    synchronized (mUserSpace) {
        UserSpace userSpace = mUserSpace.get(userId);
        if (userSpace != null)
            return userSpace;
        userSpace = new UserSpace();
        mUserSpace.put(userId, userSpace);
        return userSpace;
    }
}
```

**机制**：
- 每个用户 ID 对应一个独立的 UserSpace
- 首次访问时创建，后续复用
- 线程安全：使用 `synchronized` 保护

---

## 主要功能模块

### 1. Activity 管理

#### 启动 Activity

```java
@Override
public void startActivity(Intent intent, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mStack) {
        userSpace.mStack.startActivityLocked(userId, intent, null, null, null, -1, -1, null);
    }
}
```

**流程**：
1. 获取用户空间
2. 在用户空间的 ActivityStack 中启动 Activity

#### Activity 生命周期回调

```java
@Override
public void onActivityCreated(int taskId, IBinder token, IBinder activityRecord) {
    int callingPid = Binder.getCallingPid();
    ProcessRecord process = ProcessManagerService.get().findProcessByPid(callingPid);
    UserSpace userSpace = getOrCreateSpaceLocked(process.userId);
    synchronized (userSpace.mStack) {
        userSpace.mStack.onActivityCreated(process, taskId, token, record);
    }
}
```

**支持的回调**：
- `onActivityCreated()`: Activity 创建
- `onActivityResumed()`: Activity 恢复
- `onActivityDestroyed()`: Activity 销毁
- `onFinishActivity()`: Activity 完成

### 2. Service 管理

#### 启动 Service

```java
@Override
public ComponentName startService(Intent intent, String resolvedType, 
                                  boolean requireForeground, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mActiveServices) {
        userSpace.mActiveServices.startService(intent, resolvedType, requireForeground, userId);
    }
    return null;
}
```

**支持的操作**：
- `startService()`: 启动服务
- `stopService()`: 停止服务
- `bindService()`: 绑定服务
- `unbindService()`: 解绑服务
- `onStartCommand()`: 处理服务启动命令
- `onServiceUnbind()`: 处理服务解绑
- `onServiceDestroy()`: 处理服务销毁

#### 查看 Service

```java
@Override
public IBinder peekService(Intent intent, String resolvedType, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mActiveServices) {
        return userSpace.mActiveServices.peekService(intent, resolvedType, userId);
    }
}
```

### 3. Broadcast 管理

#### 发送 Broadcast

```java
@Override
public Intent sendBroadcast(Intent intent, String resolvedType, int userId) {
    // 查询接收者
    List<ResolveInfo> resolves = PackageManagerService.get()
        .queryBroadcastReceivers(intent, GET_META_DATA, resolvedType, userId);
    
    // 绑定应用并准备接收
    for (ResolveInfo resolve : resolves) {
        ProcessRecord processRecord = ProcessManagerService.get()
            .findProcessRecord(resolve.activityInfo.packageName, 
                              resolve.activityInfo.processName, userId);
        if (processRecord == null) {
            continue;
        }
        processRecord.bActivityThread.bindApplication();
    }
    
    // 创建影子 Intent
    Intent shadow = new Intent();
    shadow.setPackage(PrisonCore.getPackageName());
    shadow.setComponent(null);
    shadow.setAction(intent.getAction());
    return shadow;
}
```

**流程**：
1. 查询 BroadcastReceiver
2. 启动或绑定目标进程
3. 创建影子 Intent 返回

#### 调度 BroadcastReceiver

```java
@Override
public void scheduleBroadcastReceiver(Intent intent, PendingResultData pendingResultData, 
                                     int userId) {
    List<ResolveInfo> resolves = PackageManagerService.get()
        .queryBroadcastReceivers(intent, GET_META_DATA, null, userId);
    
    if (resolves.isEmpty()) {
        pendingResultData.build().finish();
        return;
    }
    
    mBroadcastManager.sendBroadcast(pendingResultData);
    
    // 发送给所有接收者
    for (ResolveInfo resolve : resolves) {
        ProcessRecord processRecord = ProcessManagerService.get()
            .findProcessRecord(resolve.activityInfo.packageName, 
                              resolve.activityInfo.processName, userId);
        if (processRecord != null) {
            ReceiverData data = new ReceiverData();
            data.intent = intent;
            data.activityInfo = resolve.activityInfo;
            data.data = pendingResultData;
            processRecord.bActivityThread.scheduleReceiver(data);
        }
    }
}
```

### 4. ContentProvider 管理

#### 获取 ContentProvider

```java
@Override
public IBinder acquireContentProviderClient(ProviderInfo providerInfo) {
    int callingPid = Binder.getCallingPid();
    ProcessRecord processRecord = ProcessManagerService.get()
        .startProcessLocked(providerInfo.packageName,
                           providerInfo.processName,
                           ProcessManagerService.get().getUserIdByCallingPid(callingPid),
                           -1,
                           Binder.getCallingPid());
    if (processRecord == null) {
        throw new RuntimeException("Unable to create process " + providerInfo.name);
    }
    return processRecord.bActivityThread.acquireContentProviderClient(providerInfo);
}
```

**流程**：
1. 启动 Provider 所在进程
2. 通过 ActivityThread 获取 Provider Client

### 5. 进程管理

#### 初始化进程

```java
@Override
public AppConfig initProcess(String packageName, String processName, int userId) {
    ProcessRecord processRecord = ProcessManagerService.get()
        .startProcessLocked(packageName, processName, userId, -1, Binder.getCallingPid());
    if (processRecord == null)
        return null;
    return processRecord.getClientConfig();
}
```

**作用**：
- 启动目标进程
- 返回进程配置信息

#### 重启进程

```java
@Override
public void restartProcess(String packageName, String processName, int userId) {
    ProcessManagerService.get().restartAppProcess(packageName, processName, userId);
}
```

### 6. IntentSender 管理

#### 注册 IntentSender

```java
@Override
public void getIntentSender(IBinder target, String packageName, int uid, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mIntentSenderRecords) {
        PendingIntentRecord record = new PendingIntentRecord();
        record.uid = uid;
        record.packageName = packageName;
        userSpace.mIntentSenderRecords.put(target, record);
    }
}
```

#### 查询 IntentSender

```java
@Override
public String getPackageForIntentSender(IBinder target, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mIntentSenderRecords) {
        PendingIntentRecord record = userSpace.mIntentSenderRecords.get(target);
        if (record != null) {
            return record.packageName;
        }
    }
    return null;
}
```

---

## 工作流程

### 1. 请求到达
- 通过 AIDL 接口 `IPActivityManagerService` 接收调用
- 获取调用者 PID 和用户 ID

### 2. 用户空间获取
```java
UserSpace userSpace = getOrCreateSpaceLocked(userId);
```
- 根据 `userId` 获取或创建用户空间
- 确保线程安全

### 3. 同步执行
```java
synchronized (userSpace.mStack) {
    // 操作 ActivityStack
}
```
- 在用户空间的相应组件上同步执行操作
- 使用 `synchronized` 保护关键操作

### 4. 进程管理
```java
ProcessRecord processRecord = ProcessManagerService.get()
    .startProcessLocked(packageName, processName, userId, ...);
```
- 需要时通过 `ProcessManagerService` 启动/管理进程
- 获取 `ProcessRecord` 用于后续操作

### 5. 生命周期回调
```java
processRecord.bActivityThread.scheduleReceiver(data);
```
- 通过 `ProcessRecord.bActivityThread` 与客户端通信
- 发送生命周期事件和回调

### 完整流程图

```
客户端请求
    ↓
AIDL 接口接收
    ↓
获取调用者信息 (PID, userId)
    ↓
getOrCreateSpaceLocked(userId)
    ↓
获取用户空间 (UserSpace)
    ├─ mStack (ActivityStack)
    ├─ mActiveServices (ActiveServices)
    └─ mIntentSenderRecords
    ↓
同步执行操作
    ↓
需要进程？
    ├─ 是 → ProcessManagerService.startProcessLocked()
    └─ 否 → 直接操作
    ↓
通过 ProcessRecord.bActivityThread 通信
    ↓
返回结果
```

---

## 关键特性

### 1. 多用户隔离
- **机制**: 每个用户拥有独立的 `UserSpace`
- **实现**: `Map<Integer, UserSpace> mUserSpace`
- **优势**: 完全隔离，互不干扰

### 2. 线程安全
- **机制**: 使用 `synchronized` 保护关键操作
- **范围**: UserSpace 获取、组件操作
- **优势**: 避免并发问题

### 3. 进程生命周期管理
- **集成**: 与 `ProcessManagerService` 紧密集成
- **功能**: 启动、重启、查找进程
- **优势**: 统一管理进程生命周期

### 4. 代理机制
- **Activity**: 通过 `ProxyActivity` 实现虚拟化
- **Service**: 通过 `ProxyService` 实现虚拟化
- **优势**: 在虚拟环境中运行目标组件

### 5. 统一接口
- **AIDL**: 通过 `IPActivityManagerService` 提供统一接口
- **实现**: 所有操作都通过 AIDL 接口
- **优势**: 跨进程调用，标准化接口

---

## 类关系图

```
ActivityManagerService
    │
    ├─ UserSpace (每个用户一个)
    │   ├─ ActivityStack
    │   │   ├─ TaskRecord[]
    │   │   │   └─ ActivityRecord[]
    │   │   └─ ProcessRecord
    │   │
    │   ├─ ActiveServices
    │   │   ├─ RunningServiceRecord[]
    │   │   └─ ConnectedServiceRecord[]
    │   │
    │   └─ mIntentSenderRecords
    │       └─ PendingIntentRecord[]
    │
    ├─ PackageManagerService
    │   └─ 解析组件信息
    │
    ├─ ProcessManagerService
    │   └─ 管理进程生命周期
    │
    └─ BroadcastManager
        └─ 管理 Broadcast 发送
```

---

## 初始化

### 系统就绪

```java
@Override
public void systemReady() {
    mBroadcastManager.startup();
}
```

**作用**：
- 系统就绪时调用
- 启动 BroadcastManager

### 构造函数

```java
public ActivityManagerService() {
    mBroadcastManager = BroadcastManager.startSystem(this, mPms);
}
```

**初始化**：
- 创建 BroadcastManager
- 传入自身和 PackageManagerService 引用

---

## 总结

`ActivityManagerService` 作为虚拟化环境的核心服务，通过以下机制实现组件管理：

1. **用户隔离**: 每个用户拥有独立的 UserSpace
2. **组件管理**: 统一管理 Activity、Service、Broadcast、ContentProvider
3. **进程集成**: 与 ProcessManagerService 紧密集成
4. **线程安全**: 使用 synchronized 保护关键操作
5. **代理机制**: 通过 Proxy 组件实现虚拟化

这种设计实现了多用户环境下的组件管理，确保每个用户的组件在独立的空间中运行，互不干扰。
