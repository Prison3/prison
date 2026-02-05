# Android 四大组件虚拟化工作原理

## 目录

1. [概述](#概述)
2. [核心设计理念](#核心设计理念)
3. [Activity 虚拟化](#activity-虚拟化)
4. [Service 虚拟化](#service-虚拟化)
5. [BroadcastReceiver 虚拟化](#broadcastreceiver-虚拟化)
6. [ContentProvider 虚拟化](#contentprovider-虚拟化)
7. [代理组件管理](#代理组件管理)
8. [多用户隔离机制](#多用户隔离机制)
9. [工作流程总结](#工作流程总结)
10. [关键技术点](#关键技术点)

---

## 概述

Android 四大组件（Activity、Service、BroadcastReceiver、ContentProvider）的虚拟化是 Prison 框架的核心功能。通过代理替换机制，实现在不修改原始应用代码的情况下，为每个应用提供独立的运行环境，实现多用户隔离和组件管理。

### 虚拟化的意义

- **多用户隔离**：每个应用运行在独立的用户空间中，互不干扰
- **组件管理**：统一管理所有应用的组件生命周期
- **透明代理**：对应用层完全透明，无需修改应用代码
- **资源隔离**：每个应用拥有独立的进程、数据存储和权限空间

---

## 核心设计理念

### 1. 代理替换机制

虚拟化的核心思想是**两步替换**：

1. **替换阶段**：将原始组件的 ComponentName 替换为代理组件
2. **恢复阶段**：在代理组件中恢复原始组件并执行

```
原始组件 → 代理组件 → 恢复原始组件
```

### 2. 信息保存与传递

通过 Intent 的 Extra 字段保存原始组件信息：

- 原始 Intent
- 组件信息（ActivityInfo/ServiceInfo）
- 用户 ID
- 其他必要的元数据

### 3. 代理组件池

预定义多个代理组件（P0-P49），避免组件冲突：

- 每个进程使用不同的代理组件
- 根据进程 ID（vpid）动态选择
- 支持并发启动多个组件

---

## Activity 虚拟化

### 架构设计

Activity 虚拟化通过 `ProxyActivity` 实现，支持 50 个代理 Activity（P0-P49）。

#### 核心类

```java
// 代理 Activity
ProxyActivity (P0-P49)

// Activity 记录
ProxyActivityRecord

// Activity 栈管理
ActivityStack
```

### 工作流程

#### 1. Activity 启动流程

```
客户端调用
    ↓
PActivityManager.startActivity()
    ↓
ActivityManagerService.startActivity()
    ↓
获取 UserSpace
    ↓
ActivityStack.startActivityLocked()
    ↓
解析 Intent → 查找/创建 TaskRecord
    ↓
处理启动模式 → 创建 ActivityRecord
    ↓
生成代理 Intent → 替换 ComponentName
    ↓
保存原始信息 → ProxyActivityRecord.saveStub()
    ↓
启动 ProxyActivity
    ↓
ProxyActivity.onCreate() → 恢复原始 Activity
    ↓
onActivityCreated() → 加入任务栈
```

#### 2. 代理替换实现

**保存原始信息**：

```java
public static void saveStub(Intent shadow, Intent target, 
                            ActivityInfo activityInfo, 
                            IBinder activityRecord, int userId) {
    shadow.putExtra("_B_|_user_id_", userId);
    shadow.putExtra("_B_|_activity_info_", activityInfo);
    shadow.putExtra("_B_|_target_", target);
    BundleCompat.putBinder(shadow, "_B_|_activity_record_v_", activityRecord);
}
```

**恢复原始 Activity**：

```java
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    finish();
    
    ProxyActivityRecord record = ProxyActivityRecord.create(getIntent());
    if (record.mTarget != null) {
        record.mTarget.setExtrasClassLoader(PActivityThread.getApplication().getClassLoader());
        startActivity(record.mTarget);  // 启动原始 Activity
    }
}
```

### 关键特性

#### 启动模式处理

- **standard**：每次创建新实例
- **singleTop**：栈顶复用
- **singleTask**：任务栈内唯一
- **singleInstance**：独立任务栈

#### 任务栈管理

- 每个用户拥有独立的 ActivityStack
- 通过 TaskRecord 管理任务栈
- 支持任务栈同步机制

#### 生命周期管理

- 通过 HCallbackProxy Hook 消息循环
- 拦截 Activity 生命周期回调
- 同步到 ActivityStack

### 代码示例

```java
// 代理 Activity 选择
String proxyActivity = ProxyManifest.getProxyActivity(vpid % 50);

// 替换 ComponentName
Intent proxyIntent = new Intent(originalIntent);
proxyIntent.setComponent(new ComponentName(
    PrisonCore.getPackageName(), 
    proxyActivity
));

// 保存原始信息
ProxyActivityRecord.saveStub(proxyIntent, originalIntent, 
                             activityInfo, activityRecord, userId);
```

---

## Service 虚拟化

### 架构设计

Service 虚拟化通过 `ProxyService` 实现，支持 50 个代理 Service（P0-P49）。

#### 核心类

```java
// 代理 Service
ProxyService (P0-P49)

// Service 记录
ProxyServiceRecord

// Service 分发器
AppServiceDispatcher

// Service 管理
ActiveServices
```

### 工作流程

#### 1. Service 启动流程

```
客户端调用
    ↓
PActivityManager.startService()
    ↓
ActivityManagerService.startService()
    ↓
获取 UserSpace
    ↓
ActiveServices.startService()
    ↓
解析 Service → 启动进程
    ↓
创建 RunningServiceRecord
    ↓
生成代理 Intent → ProxyService
    ↓
保存原始信息 → ProxyServiceRecord.saveStub()
    ↓
启动 ProxyService
    ↓
ProxyService.onStartCommand() → AppServiceDispatcher
    ↓
分发到原始 Service
```

#### 2. 代理 Service 实现

**ProxyService 核心代码**：

```java
public class ProxyService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppServiceDispatcher.get().onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return AppServiceDispatcher.get().onBind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppServiceDispatcher.get().onDestroy();
    }
}
```

**Service 记录保存**：

```java
public static void saveStub(Intent shadow, Intent target, 
                            ServiceInfo serviceInfo, 
                            IBinder token, int userId, int startId) {
    shadow.putExtra("_B_|_target_", target);
    shadow.putExtra("_B_|_service_info_", serviceInfo);
    shadow.putExtra("_B_|_user_id_", userId);
    shadow.putExtra("_B_|_start_id_", startId);
    BundleCompat.putBinder(shadow, "_B_|_token_", token);
}
```

### 关键特性

#### Service 生命周期管理

- `onCreate()`：Service 创建
- `onStartCommand()`：启动命令处理
- `onBind()`：绑定服务处理
- `onDestroy()`：Service 销毁

#### 前台服务支持

- 支持前台服务通知
- 兼容 Android 8.0+ 前台服务限制
- 自动创建通知渠道

#### 多进程支持

- 每个进程使用独立的代理 Service
- 避免 Service 冲突
- 支持并发启动

---

## BroadcastReceiver 虚拟化

### 架构设计

BroadcastReceiver 虚拟化通过 `ProxyBroadcastReceiver` 实现。

#### 核心类

```java
// 代理 BroadcastReceiver
ProxyBroadcastReceiver

// Broadcast 记录
ProxyBroadcastRecord

// Broadcast 管理
BroadcastManager
```

### 工作流程

#### 1. Broadcast 发送流程

```
客户端调用
    ↓
PActivityManager.sendBroadcast()
    ↓
ActivityManagerService.sendBroadcast()
    ↓
获取 UserSpace
    ↓
BroadcastManager.sendBroadcast()
    ↓
查找匹配的 Receiver
    ↓
生成代理 Intent → ProxyBroadcastReceiver
    ↓
保存原始信息 → ProxyBroadcastRecord.saveStub()
    ↓
发送到 ProxyBroadcastReceiver
```

#### 2. 代理 Receiver 实现

**ProxyBroadcastReceiver 核心代码**：

```java
public class ProxyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setExtrasClassLoader(context.getClassLoader());
        ProxyBroadcastRecord record = ProxyBroadcastRecord.create(intent);
        if (record.mIntent == null) {
            return;
        }
        
        PendingResult pendingResult = goAsync();
        PActivityManager.get().scheduleBroadcastReceiver(
            record.mIntent, 
            new PendingResultData(pendingResult), 
            record.mUserId
        );
    }
}
```

**Broadcast 记录保存**：

```java
public static void saveStub(Intent shadow, Intent target, int userId) {
    shadow.putExtra("_B_|_target_", target);
    shadow.putExtra("_B_|_user_id_", userId);
}
```

### 关键特性

#### 异步处理

- 使用 `goAsync()` 实现异步处理
- 避免 ANR（Application Not Responding）
- 支持长时间运行的 Broadcast

#### 动态注册支持

- 支持静态注册的 Receiver
- 支持动态注册的 Receiver
- 统一管理所有 Receiver

#### 用户隔离

- 每个用户的 Broadcast 独立处理
- 避免跨用户广播泄露
- 支持用户级别的权限控制

---

## ContentProvider 虚拟化

### 架构设计

ContentProvider 虚拟化通过 `ProxyContentProvider` 和 `IContentProviderProxy` 实现。

#### 核心类

```java
// 代理 ContentProvider
ProxyContentProvider (P0-P49)

// ContentProvider 代理
IContentProviderProxy

// ContentResolver 代理
ContentResolverProxy
```

### 工作流程

#### 1. ContentProvider 获取流程

```
客户端调用
    ↓
ContentResolver.query()
    ↓
ContentResolverProxy.query()
    ↓
IContentProviderProxy.acquireProvider()
    ↓
查找/创建 ContentProvider
    ↓
返回代理 Provider 或原始 Provider
```

#### 2. 代理 Provider 实现

**ProxyContentProvider 核心代码**：

```java
public class ProxyContentProvider extends ContentProvider {
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, 
                      @Nullable Bundle extras) {
        if (method.equals("_Black_|_init_process_")) {
            // 进程初始化
            extras.setClassLoader(AppConfig.class.getClassLoader());
            AppConfig appConfig = extras.getParcelable(AppConfig.KEY);
            PActivityThread.currentActivityThread().initializeProcess(appConfig);
            
            Bundle bundle = new Bundle();
            BundleCompat.putBinder(bundle, "_Black_|_client_", 
                                  PActivityThread.currentActivityThread());
            return bundle;
        }
        return super.call(method, arg, extras);
    }
}
```

**Provider Authority 管理**：

```java
public static String getProxyAuthorities(int index) {
    return String.format(Locale.CHINA, 
        "%s.proxy_content_provider_%d", 
        PrisonCore.getPackageName(), 
        index
    );
}
```

### 关键特性

#### 进程初始化

- 通过 `call()` 方法实现进程初始化
- 传递 AppConfig 配置信息
- 返回 ActivityThread Binder 引用

#### Authority 虚拟化

- 每个进程使用独立的 Authority
- 避免 Authority 冲突
- 支持多进程并发访问

#### 数据隔离

- 每个应用拥有独立的数据存储
- 通过用户 ID 隔离数据
- 支持跨应用数据访问控制

---

## 代理组件管理

### ProxyManifest

`ProxyManifest` 负责管理所有代理组件的名称生成。

#### 代理组件类型

```java
public class ProxyManifest {
    // 代理 Activity
    public static String getProxyActivity(int index);
    
    // 代理 Service
    public static String getProxyService(int index);
    
    // 代理 ContentProvider Authority
    public static String getProxyAuthorities(int index);
    
    // 代理 BroadcastReceiver
    public static String getProxyReceiver();
    
    // 代理 PendingActivity
    public static String getProxyPendingActivity(int index);
    
    // 代理 JobService
    public static String getProxyJobService(int index);
}
```

#### 组件选择策略

- **基于进程 ID**：`vpid % FREE_COUNT`
- **避免冲突**：每个进程使用不同的代理组件
- **支持并发**：最多支持 50 个并发进程

### 代理组件注册

所有代理组件需要在 `AndroidManifest.xml` 中注册：

```xml
<!-- 代理 Activity -->
<activity android:name=".proxy.ProxyActivity$P0" ... />
<activity android:name=".proxy.ProxyActivity$P1" ... />
<!-- ... P2-P49 ... -->

<!-- 代理 Service -->
<service android:name=".proxy.ProxyService$P0" ... />
<!-- ... P1-P49 ... -->

<!-- 代理 ContentProvider -->
<provider 
    android:name=".proxy.ProxyContentProvider$P0"
    android:authorities="xxx.proxy_content_provider_0" ... />
<!-- ... P1-P49 ... -->

<!-- 代理 BroadcastReceiver -->
<receiver android:name=".proxy.ProxyBroadcastReceiver" ... />
```

---

## 多用户隔离机制

### UserSpace 架构

每个用户拥有独立的 `UserSpace`，包含：

```java
public class UserSpace {
    // Activity 栈管理
    public final ActivityStack mStack = new ActivityStack();
    
    // Service 管理
    public final ActiveServices mActiveServices = new ActiveServices();
    
    // IntentSender 记录
    public final Map<IBinder, PendingIntentRecord> mIntentSenderRecords;
}
```

### 用户空间获取

```java
private UserSpace getOrCreateSpaceLocked(int userId) {
    synchronized (mUserSpace) {
        UserSpace userSpace = mUserSpace.get(userId);
        if (userSpace != null) {
            return userSpace;
        }
        userSpace = new UserSpace();
        mUserSpace.put(userId, userSpace);
        return userSpace;
    }
}
```

### 隔离机制

- **进程隔离**：每个应用运行在独立进程
- **数据隔离**：每个用户拥有独立的数据存储
- **权限隔离**：每个用户拥有独立的权限空间
- **组件隔离**：组件之间互不干扰

---

## 工作流程总结

### 统一虚拟化流程

```
1. 客户端请求
   ↓
2. ActivityManagerService 接收
   ↓
3. 获取/创建 UserSpace
   ↓
4. 根据组件类型选择处理方式
   ├─ Activity → ActivityStack
   ├─ Service → ActiveServices
   ├─ Broadcast → BroadcastManager
   └─ ContentProvider → IContentProviderProxy
   ↓
5. 生成代理 Intent
   ↓
6. 保存原始组件信息到 Record
   ↓
7. 替换 ComponentName 为代理组件
   ↓
8. 启动/调用代理组件
   ↓
9. 代理组件恢复原始组件
   ↓
10. 执行原始组件逻辑
```

### 关键步骤说明

1. **信息保存**：将原始组件信息保存到 Intent Extra
2. **组件替换**：将 ComponentName 替换为代理组件
3. **代理执行**：代理组件接收系统调用
4. **信息恢复**：从 Intent Extra 恢复原始组件信息
5. **原始执行**：执行原始组件的实际逻辑

---

## 关键技术点

### 1. Intent 信息传递

通过 Intent 的 Extra 字段传递原始组件信息：

- 使用特殊前缀 `_B_|_` 避免冲突
- 支持 Parcelable 对象传递
- 支持 Binder 对象传递（通过 BundleCompat）

### 2. ClassLoader 管理

确保正确的类加载：

```java
intent.setExtrasClassLoader(PActivityThread.getApplication().getClassLoader());
```

### 3. 进程管理

- 每个应用运行在独立进程
- 进程名称格式：`包名:p进程ID`
- 通过 ProcessManagerService 管理进程生命周期

### 4. Hook 机制

通过 InjectorManager 注入代理：

- Hook 系统服务（IActivityManager、IPackageManager 等）
- Hook 消息循环（HCallbackProxy）
- Hook ContentResolver（ContentResolverProxy）

### 5. 线程安全

- UserSpace 操作使用 `synchronized` 保护
- ActivityStack 和 ActiveServices 独立锁
- 避免死锁和竞态条件

### 6. 生命周期同步

- 通过回调机制同步组件生命周期
- 维护组件状态一致性
- 处理异常情况（进程崩溃、组件销毁等）

---

## 总结

Android 四大组件虚拟化通过代理替换机制实现了：

1. **透明代理**：对应用层完全透明
2. **多用户隔离**：每个应用独立运行环境
3. **统一管理**：集中管理所有组件生命周期
4. **灵活扩展**：支持多种组件类型和场景

这种设计使得 Prison 框架能够在不需要修改原始应用代码的情况下，为每个应用提供独立的运行环境，实现了真正的应用虚拟化。

---

## 相关文档

- [ActivityStack 工作原理](./ActivityStack工作原理.md)
- [ActivityManagerService 工作原理](./ActivityManagerService工作原理.md)
- [InjectorManager 代理类功能说明](./InjectorManager代理类功能说明.md)

---

## 更新日志

- 2024-02-02: 创建初始文档
  - 详细介绍四大组件虚拟化工作原理
  - 包含完整的工作流程和代码示例
