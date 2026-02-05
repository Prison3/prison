# InjectorManager 代理类功能说明文档

## 概述

`InjectorManager` 是虚拟化框架的核心注入管理器，负责管理和注入所有系统服务的代理类。这些代理类通过 Hook 机制拦截和重定向系统服务调用，实现多用户环境下的虚拟化。

本文档详细说明 `InjectorManager.java` 中第 103-187 行注册的所有代理类的功能与作用。

---

## 目录

1. [核心系统服务代理](#核心系统服务代理)
2. [Activity 和任务管理代理](#activity-和任务管理代理)
3. [包和权限管理代理](#包和权限管理代理)
4. [通知和提醒服务代理](#通知和提醒服务代理)
5. [网络和连接服务代理](#网络和连接服务代理)
6. [存储和文件系统代理](#存储和文件系统代理)
7. [媒体和音频服务代理](#媒体和音频服务代理)
8. [位置和传感器服务代理](#位置和传感器服务代理)
9. [用户和账户管理代理](#用户和账户管理代理)
10. [设备标识和硬件服务代理](#设备标识和硬件服务代理)
11. [WebView 和浏览器代理](#webview-和浏览器代理)
12. [数据库和存储代理](#数据库和存储代理)
13. [安全和管理服务代理](#安全和管理服务代理)
14. [MIUI 特定代理](#miui-特定代理)
15. [其他工具类代理](#其他工具类代理)

---

## 核心系统服务代理

### IDisplayManagerProxy

**功能**: Hook `IDisplayManager` 服务，管理显示设备相关操作

**Hook 的服务**: `IDisplayManager` (通过 `ServiceManager.getService("display")`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IDisplayManager` 的 Binder 调用
- 替换系统服务 `"display"`，使虚拟应用获得独立的显示环境
- 虚拟化显示设备信息，避免显示相关的权限问题

**Hook 的方法**:
- 所有 `IDisplayManager` 接口方法都会被代理
- 主要处理显示设备查询、显示配置等操作

**使用场景**:
- 虚拟应用查询显示设备信息时
- 需要获取屏幕尺寸、DPI 等显示参数时
- 处理多显示器场景时

**注意事项**:
- 需要确保显示服务正常可用
- 某些显示操作可能需要特殊权限处理

---

### OsStub

**功能**: Hook `Os` 类，拦截底层系统调用

**Hook 的类**: `libcore.io.Os` (静态类)

**实现原理**:
- Hook `Os` 类的静态方法，拦截文件系统、进程、网络等底层操作
- 重定向文件路径到虚拟目录
- 虚拟化进程和线程管理
- 拦截网络相关的系统调用

**Hook 的方法**:
- `open()`: 文件打开操作
- `stat()`: 文件状态查询
- `chmod()`, `chown()`: 文件权限操作
- `fork()`, `exec()`: 进程操作
- `socket()`, `connect()`: 网络操作

**使用场景**:
- 虚拟应用进行文件操作时
- 需要创建进程或线程时
- 进行网络连接时

**注意事项**:
- 底层系统调用拦截可能影响性能
- 需要正确处理文件路径重定向

---

### ContentServiceStub

**功能**: Hook `ContentService`，管理内容提供者服务

**Hook 的服务**: `IContentService` (通过 `ServiceManager.getService("content")`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IContentService` 的调用
- 替换系统服务 `"content"`，管理内容提供者的注册和查询
- 虚拟化内容提供者访问，确保虚拟应用能正确访问内容提供者

**Hook 的方法**:
- `registerContentObserver()`: 注册内容观察者
- `notifyChange()`: 通知内容变化
- `getSyncAdapterTypes()`: 获取同步适配器类型

**使用场景**:
- 虚拟应用注册内容观察者时
- 内容提供者数据变化通知时
- 同步适配器查询时

**注意事项**:
- 需要确保内容提供者正确注册
- 内容变化通知需要正确传递

---

### ContentResolverProxy

**功能**: Hook `ContentResolver`，拦截内容解析器调用

**Hook 的类**: `android.content.ContentResolver` (类方法)

**实现原理**:
- Hook `ContentResolver` 的静态和实例方法
- 拦截内容 URI 查询、插入、更新、删除操作
- 重定向内容提供者查询到虚拟环境
- 处理内容提供者权限检查

**Hook 的方法**:
- `query()`: 查询内容提供者数据
- `insert()`: 插入数据
- `update()`: 更新数据
- `delete()`: 删除数据
- `acquireContentProviderClient()`: 获取内容提供者客户端

**使用场景**:
- 虚拟应用访问 MediaStore、Contacts 等内容提供者时
- 需要查询或修改系统数据时
- 跨应用数据共享时

**注意事项**:
- 需要正确处理内容 URI 的权限
- 某些系统内容提供者可能需要特殊处理

---

## Activity 和任务管理代理

### IActivityManagerProxy

**功能**: Hook `IActivityManager` 服务，管理 Activity 生命周期

**Hook 的服务**: `IActivityManager` (通过 `ActivityManagerNative.getDefault()` 或 `IActivityManagerSingleton`)

**实现原理**:
- 通过 `ClassInvocationStub` 拦截 `IActivityManager` 接口的所有方法调用
- 替换 `IActivityManager` 的单例实例 (`BRSingleton._set_mInstance()`)
- 拦截 Activity、Service、Broadcast 相关的所有操作
- 重定向到虚拟环境的 `PActivityManager` 进行处理

**Hook 的关键方法** (共 30+ 个):

1. **Activity 管理**:
   - `startActivity()`: 拦截 Activity 启动，重定向到 `PActivityManager.startActivity()`
   - `startActivityAsUser()`: 多用户 Activity 启动
   - `finishActivity()`: Activity 完成
   - `getAppTasks()`: 返回虚拟任务栈 (`RunningAppProcessInfo`)
   - `getRunningAppProcesses()`: 返回虚拟进程列表
   - `getCallingPackage()`, `getCallingActivity()`: 获取调用者信息

2. **Service 管理**:
   - `startService()`: 启动服务，重定向到 `PActivityManager.startService()`
   - `stopService()`, `stopServiceToken()`: 停止服务
   - `bindService()`, `bindIsolatedService()`, `bindServiceInstance()`: 绑定服务
   - `unbindService()`: 解绑服务
   - `peekService()`: 查看服务状态
   - `setServiceForeground()`: 设置前台服务（Android 14+ 移除服务类型限制）

3. **Broadcast 管理**:
   - `broadcastIntent()`, `broadcastIntentWithFeature()`: 发送广播
   - `registerReceiver()`, `registerReceiverWithFeature()`: 注册接收者
   - `unregisterReceiver()`: 注销接收者
   - `finishReceiver()`: 完成接收者

4. **IntentSender 管理**:
   - `getIntentSender()`, `getIntentSenderWithSourceToken()`, `getIntentSenderWithFeature()`: 获取 IntentSender
   - `getPackageForIntentSender()`: 获取 IntentSender 的包名
   - `getUidForIntentSender()`: 获取 IntentSender 的 UID
   - `sendIntentSender()`: 发送 IntentSender

5. **权限管理**:
   - `checkPermission()`: 检查权限（自动授予音频、存储权限）
   - `checkUriPermission()`: 检查 URI 权限（始终授予）

6. **其他**:
   - `getContentProvider()`: 获取内容提供者（错误处理）
   - `getCurrentUser()`: 返回虚拟用户信息
   - `getHistoricalProcessExitReasons()`: 返回空列表
   - `setActivityLocusContext()`: 设置 Activity 上下文（包名检查）

**代码示例**:
```java
// startService 的实现
@ProxyMethod("startService")
public static class StartService extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        Intent intent = (Intent) args[1];
        String resolvedType = (String) args[2];
        ResolveInfo resolveInfo = PPackageManager.get()
            .resolveService(intent, 0, resolvedType, PActivityThread.getUserId());
        if (resolveInfo == null) {
            return method.invoke(who, args);
        }
        boolean requireForeground = (boolean) args[3]; // Android 8+
        return PActivityManager.get()
            .startService(intent, resolvedType, requireForeground, PActivityThread.getUserId());
    }
}
```

**使用场景**:
- 虚拟应用启动 Activity 时
- 启动或绑定 Service 时
- 发送或接收 Broadcast 时
- 创建 PendingIntent 时
- 检查权限时

**注意事项**:
- 所有 Activity 启动都会重定向到虚拟环境
- Service 绑定会创建代理 ServiceConnection
- Broadcast 会创建代理 IntentReceiver
- 权限检查会自动授予某些权限（音频、存储等）
- 错误处理：SecurityException 会返回安全的默认值

### IActivityTaskManagerProxy
**功能**: Hook `IActivityTaskManager` 服务（Android 10+），管理任务栈  
**作用**:
- 管理 Activity 任务栈
- 处理任务切换和恢复
- 虚拟化任务记录

### HCallbackProxy

**功能**: Hook `ActivityThread.H` 的 `Callback`，拦截消息循环

**Hook 的对象**: `ActivityThread.mH.mCallback` (Handler.Callback)

**实现原理**:
- 替换 `ActivityThread` 的 `Handler` 的 `Callback`
- 在消息分发层面拦截所有发送到 `ActivityThread` 的消息
- 保存原有的 `Callback`，未处理的消息会转发给原有 `Callback`
- 使用 `AtomicBoolean` 防止重入

**Hook 的消息类型**:

1. **Activity 启动消息**:
   - Android 9+ (Pie): `EXECUTE_TRANSACTION` (包含 `LaunchActivityItem`)
   - Android 8 及以下: `LAUNCH_ACTIVITY`

2. **Service 创建消息**:
   - `CREATE_SERVICE`

**核心方法**:

1. **`handleMessage(Message msg)`**:
   - 拦截 `LAUNCH_ACTIVITY` 或 `EXECUTE_TRANSACTION` 消息
   - 调用 `handleLaunchActivity()` 处理
   - 如果处理成功，将消息重新加入队列并返回 `true`
   - 拦截 `CREATE_SERVICE` 消息，调用 `handleCreateService()`

2. **`handleLaunchActivity(Object client)`**:
   - 从 `ClientTransaction` 或 `ActivityClientRecord` 中提取 Intent
   - 通过 `ProxyActivityRecord.create()` 解析代理信息
   - **关键操作**:
     - 如果进程未初始化：重启进程并重新保存 stub 信息
     - 如果 Application 未绑定：绑定 Application
     - 如果已初始化：恢复原始 Intent 和 ActivityInfo
   - 替换 `ActivityClientRecord` 或 `LaunchActivityItem` 中的 Intent 和 ActivityInfo
   - 通知 `PActivityManager.onActivityCreated()`

3. **`handleCreateService(Object data)`**:
   - 检查是否是 ProxyService（避免循环）
   - 如果不是，通过 `PActivityManager.startService()` 启动真实 Service
   - 返回 `true` 阻止系统创建 Service

**代码示例**:
```java
@Override
public boolean handleMessage(@NonNull Message msg) {
    if (!mBeing.getAndSet(true)) {
        try {
            if (BuildCompat.isPie()) {
                if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION()) {
                    if (handleLaunchActivity(msg.obj)) {
                        getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                        return true; // 已处理，不继续传递
                    }
                }
            } else {
                if (msg.what == BRActivityThreadH.get().LAUNCH_ACTIVITY()) {
                    if (handleLaunchActivity(msg.obj)) {
                        getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                        return true;
                    }
                }
            }
            // ... 其他消息处理
        } finally {
            mBeing.set(false);
        }
    }
    return false;
}
```

**使用场景**:
- 系统启动 ProxyActivity 时
- 需要将 ProxyActivity 替换回真实 Activity 时
- Service 创建时

**注意事项**:
- **这是虚拟化框架最核心的组件**，必须正常工作
- 正常情况下，`ProxyActivity.onCreate()` 不会执行，因为消息已被拦截
- 如果 hook 失败，`ProxyActivity.onCreate()` 会作为备用方案
- 需要处理不同 Android 版本的消息格式差异
- 线程安全：使用 `AtomicBoolean` 防止重入

---

## 包和权限管理代理

### IPackageManagerProxy

**功能**: Hook `IPackageManager` 服务，管理包安装和查询

**Hook 的服务**: `IPackageManager` (通过 `ActivityThread.sPackageManager()`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IPackageManager` 的 Binder 调用
- 替换 `ActivityThread.sPackageManager` 和系统服务 `"package"`
- 更新 `ApplicationPackageManager.mPM` 字段
- 所有包查询都重定向到 `PPackageManager`

**Hook 的关键方法** (共 20+ 个):

1. **Intent 解析**:
   - `resolveIntent()`: 解析 Intent，重定向到 `PPackageManager.resolveIntent()`
   - `resolveService()`: 解析 Service Intent
   - `resolveContentProvider()`: 解析内容提供者

2. **包信息查询**:
   - `getPackageInfo()`: 获取包信息，重定向到 `PPackageManager.getPackageInfo()`
     - 特殊处理：为 `com.android.vending` 提供假的 Google Play Services 信息
     - 自动标记音频相关权限为已授予
   - `getApplicationInfo()`: 获取应用信息
   - `getPackageUid()`: 获取包 UID（替换包名）
   - `getInstallerPackageName()`: 返回 `"com.android.vending"` (假 Google Play)

3. **组件信息查询**:
   - `getActivityInfo()`: 获取 Activity 信息
   - `getServiceInfo()`: 获取 Service 信息
   - `getReceiverInfo()`: 获取 BroadcastReceiver 信息
   - `getProviderInfo()`: 获取 ContentProvider 信息

4. **列表查询**:
   - `getInstalledPackages()`: 获取已安装包列表
   - `getInstalledApplications()`: 获取已安装应用列表
   - `queryIntentActivities()`: 查询可处理 Intent 的 Activity
   - `queryBroadcastReceivers()`: 查询 BroadcastReceiver
   - `queryContentProviders()`: 查询内容提供者

5. **权限管理**:
   - `checkPermission()`: 检查权限（`SimpleAudioPermissionHook`）
     - 自动授予音频权限（`RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE` 等）
     - 自动授予存储/媒体权限（`READ_EXTERNAL_STORAGE`, `READ_MEDIA_*` 等）
     - 自动授予通知和 MIUI 权限
   - `checkSelfPermission()`: 检查自身权限（同上）
   - `shouldShowRequestPermissionRationale()`: 不显示权限说明（已自动授予）
   - `requestPermissions()`: 允许权限请求流程继续

6. **其他**:
   - `getPackagesForUid()`: 根据 UID 获取包名列表
   - `getComponentEnabledSetting()`: 获取组件启用状态
   - `setComponentEnabledSetting()`: 设置组件启用状态（返回 0，不实际设置）
   - `getDrawable()`: 阻止图标加载（`DisableIconLoading`）
   - `setSplashScreenTheme()`: 设置启动画面主题（绕过 UID 检查，MIUI 特殊处理）

7. **MIUI 安全绕过** (`XiaomiSecurityBypass`):
   - 拦截 30+ 个 MIUI 安全敏感方法
   - 在 MIUI/HyperOS 设备上自动启用
   - 绕过 UID 检查、包名验证等安全限制

**代码示例**:
```java
@ProxyMethod("getPackageInfo")
public static class GetPackageInfo extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        String packageName = (String) args[0];
        int flags = MethodParameterUtils.toInt(args[1]);
        
        // 为 Google Play Services 提供假信息
        if ("com.android.vending".equals(packageName)) {
            return createFakeGooglePlayServicesPackageInfo();
        }
        
        PackageInfo packageInfo = PPackageManager.get()
            .getPackageInfo(packageName, flags, PActivityThread.getUserId());
        if (packageInfo != null) {
            // 自动标记音频权限为已授予
            if (packageInfo.requestedPermissions != null) {
                for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
                    String perm = packageInfo.requestedPermissions[i];
                    if (isAudioPermission(perm)) {
                        packageInfo.requestedPermissionsFlags[i] |= 
                            PackageInfo.REQUESTED_PERMISSION_GRANTED;
                    }
                }
            }
            return packageInfo;
        }
        return null;
    }
}
```

**使用场景**:
- 虚拟应用查询包信息时
- 解析 Intent 到虚拟应用时
- 检查权限时
- 查询已安装应用列表时

**注意事项**:
- 所有包查询都重定向到虚拟环境
- 自动授予某些权限，避免权限问题
- MIUI 设备需要特殊处理，绕过安全检查
- 图标加载被阻止，避免资源问题

### IPermissionManagerProxy
**功能**: Hook `IPermissionManager` 服务，管理权限  
**作用**:
- 虚拟化权限授予和撤销
- 拦截权限检查请求
- 管理虚拟应用的权限状态

### IAppOpsManagerProxy
**功能**: Hook `IAppOpsManager` 服务，管理应用操作权限  
**作用**:
- 虚拟化 AppOps 权限检查
- 拦截敏感操作（如位置、相机等）
- 管理虚拟应用的操作权限

---

## 通知和提醒服务代理

### INotificationManagerProxy

**功能**: Hook `INotificationManager` 服务，管理通知和通知渠道

**Hook 的服务**: `INotificationManager` (通过 `NotificationManager.getService()`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `INotificationManager` 的调用
- 替换 `NotificationManager.sService` 和系统服务 `Context.NOTIFICATION_SERVICE`
- 所有通知操作都重定向到 `PNotificationManager`
- 替换所有参数中的包名为虚拟应用包名

**Hook 的方法**:

1. **通知渠道管理** (Android 8+):
   - `createNotificationChannels()`: 创建通知渠道
     - 遍历渠道列表，调用 `PNotificationManager.createNotificationChannel()`
   - `createNotificationChannelGroups()`: 创建通知渠道组
   - `getNotificationChannel()`: 获取通知渠道
     - 从 `PNotificationManager` 获取
   - `getNotificationChannels()`: 获取所有通知渠道
   - `deleteNotificationChannel()`: 删除通知渠道
   - `deleteNotificationChannelGroup()`: 删除通知渠道组
   - `getNotificationChannelGroups()`: 获取所有通知渠道组

2. **通知操作**:
   - `enqueueNotificationWithTag()`: 显示通知
     - 调用 `PNotificationManager.enqueueNotificationWithTag()`
   - `cancelNotificationWithTag()`: 取消通知
     - 调用 `PNotificationManager.cancelNotificationWithTag()`

**参数处理**:
- `invoke()` 方法中：`MethodParameterUtils.replaceAllAppPkg(args)` 替换所有包名

**代码示例**:
```java
@ProxyMethod("enqueueNotificationWithTag")
public static class EnqueueNotificationWithTag extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        String tag = (String) args[getTagIndex()];
        int id = (int) args[getIdIndex()];
        Notification notification = MethodParameterUtils
            .getFirstParam(args, Notification.class);
        PNotificationManager.get()
            .enqueueNotificationWithTag(id, tag, notification);
        return 0;
    }
}
```

**使用场景**:
- 虚拟应用显示通知时
- 创建或管理通知渠道时
- 取消通知时

**注意事项**:
- 所有通知都存储在虚拟环境中
- 通知渠道独立管理
- 包名会被自动替换

### IAlarmManagerProxy
**功能**: Hook `IAlarmManager` 服务，管理定时任务  
**作用**:
- 虚拟化闹钟和定时器
- 拦截定时任务设置
- 管理虚拟应用的定时任务

---

## 网络和连接服务代理

### IConnectivityManagerProxy

**功能**: Hook `IConnectivityManager` 服务，管理网络连接和网络状态

**Hook 的服务**: `IConnectivityManager` (通过 `ServiceManager.getService(Context.CONNECTIVITY_SERVICE)`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IConnectivityManager` 的调用
- 替换系统服务 `Context.CONNECTIVITY_SERVICE`
- 创建虚拟的 `NetworkInfo`、`NetworkCapabilities`、`LinkProperties` 对象
- 确保虚拟应用能够正常进行网络操作

**Hook 的方法** (共 20+ 个):

1. **网络信息查询**:
   - `getNetworkInfo()`: 获取网络信息（多个重载）
     - 如果原始方法失败，创建虚拟 `NetworkInfo`（WiFi，已连接）
   - `getAllNetworkInfo()`: 获取所有网络信息
     - 返回 `[WiFi NetworkInfo, Mobile NetworkInfo]`
   - `getActiveNetwork()`: 获取活动网络
     - 创建虚拟 `Network` 对象
   - `getActiveNetworkInfoForUid()`: 根据 UID 获取网络信息

2. **网络能力查询** (Android 5+):
   - `getNetworkCapabilities()`: 获取网络能力
     - 创建虚拟 `NetworkCapabilities`，包含：
       - 传输类型：WiFi、蜂窝网络
       - 能力：INTERNET、VALIDATED、TRUSTED、NOT_RESTRICTED 等
   - `isNetworkValidated()`: 网络是否已验证（返回 true）

3. **网络属性查询** (Android 5+):
   - `getLinkProperties()`: 获取链路属性
     - 创建虚拟 `LinkProperties`，设置 DNS 服务器（8.8.8.8, 8.8.4.4）
   - `getDnsServers()`: 获取 DNS 服务器
     - 返回 `[8.8.8.8, 8.8.4.4]`
   - `getPrivateDnsServerName()`: 获取私有 DNS（返回 null）
   - `isPrivateDnsActive()`: 私有 DNS 是否激活（返回 false）

4. **网络请求和回调**:
   - `requestNetwork()`: 请求网络
     - 尝试原始方法，失败时创建备用结果
   - `registerNetworkCallback()`: 注册网络回调
     - 允许注册，确保网络访问
   - `registerDefaultNetworkCallback()`: 注册默认网络回调
   - `unregisterNetworkCallback()`: 注销网络回调
   - `addDefaultNetworkActiveListener()`: 添加网络活动监听
   - `removeDefaultNetworkActiveListener()`: 移除网络活动监听

5. **其他**:
   - `isActiveNetworkMetered()`: 网络是否计费（返回 false，不计费）
   - `getNetworkForType()`: 根据类型获取网络

**代码示例**:
```java
@ProxyMethod("getNetworkInfo")
public static class GetNetworkInfo extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        try {
            Object result = method.invoke(who, args);
            if (result != null) {
                return result;
            }
            // 创建虚拟 NetworkInfo
            return createNetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        } catch (Exception e) {
            return createNetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        }
    }
    
    private Object createNetworkInfo(int type, int subType, 
                                    String typeName, String subTypeName) {
        NetworkInfo networkInfo = new NetworkInfo(type, subType, typeName, subTypeName);
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);
        return networkInfo;
    }
}
```

**使用场景**:
- 虚拟应用检查网络状态时
- 需要网络能力信息时
- 注册网络回调时
- DNS 查询时

**注意事项**:
- 确保虚拟应用能够正常进行网络操作
- 网络状态始终显示为已连接
- DNS 使用 Google DNS（8.8.8.8, 8.8.4.4）
- 网络不计费，允许完整访问

### IWifiManagerProxy
**功能**: Hook `IWifiManager` 服务，管理 WiFi  
**作用**:
- 虚拟化 WiFi 连接
- 拦截 WiFi 状态查询
- 管理 WiFi 权限（仅在 Android 10 测试）

### IWifiScannerProxy
**功能**: Hook `IWifiScanner` 服务，管理 WiFi 扫描  
**作用**:
- 虚拟化 WiFi 扫描功能
- 拦截 WiFi 扫描请求

### INetworkManagementServiceProxy
**功能**: Hook `INetworkManagementService` 服务，管理网络管理  
**作用**:
- 虚拟化网络管理操作
- 拦截网络配置请求

### IDnsResolverProxy
**功能**: Hook `IDnsResolver` 服务，管理 DNS 解析  
**作用**:
- 虚拟化 DNS 解析
- 拦截 DNS 查询请求

### IVpnManagerProxy
**功能**: Hook `IVpnManager` 服务，管理 VPN  
**作用**:
- 虚拟化 VPN 连接
- 拦截 VPN 相关操作

---

## 存储和文件系统代理

### IStorageManagerProxy

**功能**: Hook `IStorageManager` 服务，管理存储设备和存储卷

**Hook 的服务**: `IStorageManager` 或 `IMountService` (通过 `ServiceManager.getService("mount")`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截存储管理服务的调用
- 替换系统服务 `"mount"`
- 根据 Android 版本选择正确的接口（Oreo+ 使用 `IStorageManager`，否则使用 `IMountService`）
- 存储卷查询重定向到 `PStorageManager`

**Hook 的方法**:

1. **存储卷查询**:
   - `getVolumeList()`: 获取存储卷列表
     - 重定向到 `PStorageManager.getVolumeList()`
     - 参数：`uid`, `packageName`, `flags`, `userId`
     - 如果返回 null，使用原始方法

2. **目录操作**:
   - `mkdirs()`: 创建目录（返回 0，成功）

**代码示例**:
```java
@ProxyMethod("getVolumeList")
public static class GetVolumeList extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        if (args == null) {
            StorageVolume[] volumeList = PStorageManager.get()
                .getVolumeList(PActivityThread.getBoundUid(), null, 0, 
                             PActivityThread.getUserId());
            return volumeList != null ? volumeList : method.invoke(who, args);
        }
        try {
            int uid = (int) args[0];
            String packageName = (String) args[1];
            int flags = (int) args[2];
            StorageVolume[] volumeList = PStorageManager.get()
                .getVolumeList(uid, packageName, flags, PActivityThread.getUserId());
            return volumeList != null ? volumeList : method.invoke(who, args);
        } catch (Throwable t) {
            return method.invoke(who, args);
        }
    }
}
```

**使用场景**:
- 虚拟应用查询存储设备时
- 需要获取存储卷信息时
- 创建存储目录时

**注意事项**:
- 需要处理不同 Android 版本的接口差异
- 存储卷信息来自虚拟环境

### IStorageStatsManagerProxy
**功能**: Hook `IStorageStatsManager` 服务，管理存储统计  
**作用**:
- 虚拟化存储使用统计
- 拦截存储空间查询

### FileSystemProxy
**功能**: Hook 文件系统操作  
**作用**:
- 虚拟化文件路径
- 重定向文件访问到虚拟目录
- 拦截文件系统调用

---

## 媒体和音频服务代理

### IAudioServiceProxy

**功能**: Hook `IAudioService` 服务，管理音频和麦克风状态

**Hook 的服务**: `IAudioService` (通过 `ServiceManager.getService(Context.AUDIO_SERVICE)`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IAudioService` 的调用
- 替换系统服务 `Context.AUDIO_SERVICE`
- 使用多种反射路径确保兼容不同 Android 版本
- 确保麦克风始终未静音，允许音频操作

**Hook 的方法**:

1. **麦克风管理**:
   - `isMicrophoneMuted()`: 麦克风是否静音
     - **始终返回 false**（未静音）
   - `setMicrophoneMute()`: 设置麦克风静音
     - 忽略静音请求，允许录音

2. **音频焦点**:
   - 其他音频相关方法会被代理，确保音频操作正常

**代码示例**:
```java
@ProxyMethod("isMicrophoneMuted")
public static class IsMicrophoneMuted extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        Logger.d(TAG, "AudioService: isMicrophoneMuted returning false");
        return false; // 始终返回未静音
    }
}
```

**使用场景**:
- 虚拟应用需要录音时
- 音频焦点管理时
- 麦克风状态查询时

**注意事项**:
- 确保麦克风始终可用，避免录音失败
- 需要处理不同 Android 版本的接口差异

### AudioRecordProxy
**功能**: Hook `AudioRecord` 类，管理音频录制  
**作用**:
- 虚拟化音频录制
- 拦截录音权限检查
- 管理录音设备访问

### MediaRecorderProxy
**功能**: Hook `MediaRecorder` 类，管理媒体录制  
**作用**:
- 虚拟化媒体录制
- 拦截录制权限检查

### MediaRecorderClassProxy
**功能**: Hook `MediaRecorder` 类的静态方法  
**作用**:
- 拦截 MediaRecorder 的静态调用
- 虚拟化录制相关操作

### IMediaSessionManagerProxy
**功能**: Hook `IMediaSessionManager` 服务，管理媒体会话  
**作用**:
- 虚拟化媒体会话
- 拦截媒体控制请求

### IMediaRouterServiceProxy
**功能**: Hook `IMediaRouterService` 服务，管理媒体路由  
**作用**:
- 虚拟化媒体路由
- 拦截媒体投屏请求

---

## 位置和传感器服务代理

### ILocationManagerProxy

**功能**: Hook `ILocationManager` 服务，管理位置服务和虚拟位置

**Hook 的服务**: `ILocationManager` (通过 `ServiceManager.getService(Context.LOCATION_SERVICE)`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `ILocationManager` 的调用
- 替换系统服务 `Context.LOCATION_SERVICE`
- 如果虚拟位置启用，返回虚拟位置；否则使用真实位置
- 处理权限问题，避免崩溃

**Hook 的方法**:

1. **位置查询**:
   - `getLastLocation()`: 获取最后位置
     - 如果虚拟位置启用：返回 `PLocationManager.getLocation().convert2SystemLocation()`
     - 否则：调用原始方法，捕获 SecurityException
   - `getLastKnownLocation()`: 获取已知位置（同上）

2. **位置更新**:
   - `requestLocationUpdates()`: 请求位置更新
     - 如果虚拟位置启用：注册到 `PLocationManager`
     - 否则：调用原始方法，捕获 SecurityException
   - `removeUpdates()`: 移除位置更新
     - 如果虚拟位置启用：从 `PLocationManager` 移除

3. **位置提供者**:
   - `getBestProvider()`: 获取最佳提供者
     - 如果虚拟位置启用：返回 `GPS_PROVIDER`
   - `getAllProviders()`: 返回 `[GPS_PROVIDER, NETWORK_PROVIDER]`
   - `isProviderEnabledForUser()`: 检查提供者是否启用
     - 仅 `GPS_PROVIDER` 返回 true

4. **其他**:
   - `registerGnssStatusCallback()`: 注册 GNSS 状态回调（返回 true）
   - `removeGpsStatusListener()`: 移除 GPS 状态监听（返回 0）
   - `getProviderProperties()`: 获取提供者属性
     - 如果虚拟位置启用：移除网络和基站要求
   - `setExtraLocationControllerPackageEnabled()`: 设置位置控制器（返回 0）

**特殊处理**:
- Google Play Services 的位置请求会被阻止（返回 null），防止崩溃

**代码示例**:
```java
@ProxyMethod("getLastLocation")
public static class GetLastLocation extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        if (PLocationManager.isFakeLocationEnable()) {
            // 返回虚拟位置
            return PLocationManager.get()
                .getLocation(PActivityThread.getUserId(), 
                           PActivityThread.getAppPackageName())
                .convert2SystemLocation();
        }
        
        // 使用真实位置，但处理权限问题
        try {
            return method.invoke(who, args);
        } catch (Exception e) {
            if (e.getCause() instanceof SecurityException) {
                Logger.w(TAG, "Location permission denied, returning null");
                return null;
            }
            throw e;
        }
    }
}
```

**使用场景**:
- 虚拟应用需要位置信息时
- 需要保护真实位置隐私时
- 位置权限被拒绝时

**注意事项**:
- 虚拟位置需要先启用 (`PLocationManager.isFakeLocationEnable()`)
- 权限问题会返回 null 而不是崩溃
- Google Play Services 的位置请求会被阻止

### ISystemSensorManagerProxy
**功能**: Hook `ISystemSensorManager` 服务，管理传感器  
**作用**:
- 虚拟化传感器访问
- 拦截传感器数据查询
- 管理传感器权限

### ISensorPrivacyManagerProxy
**功能**: Hook `ISensorPrivacyManager` 服务，管理传感器隐私  
**作用**:
- 虚拟化传感器隐私设置
- 拦截传感器隐私检查

---

## 用户和账户管理代理

### IUserManagerProxy

**功能**: Hook `IUserManager` 服务，管理用户和多用户环境

**Hook 的服务**: `IUserManager` (通过 `ServiceManager.getService(Context.USER_SERVICE)`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IUserManager` 的调用
- 替换系统服务 `Context.USER_SERVICE`
- 返回虚拟用户信息，支持多用户环境

**Hook 的方法**:

1. **应用限制**:
   - `getApplicationRestrictions()`: 获取应用限制
     - 将包名参数替换为宿主包名

2. **用户信息**:
   - `getProfileParent()`: 获取配置文件父用户
     - 返回虚拟用户信息：`UserInfo(userId, "Prison", FLAG_PRIMARY)`
   - `getUsers()`: 获取用户列表
     - 返回空列表 `new ArrayList<>()`

**代码示例**:
```java
@ProxyMethod("getProfileParent")
public static class GetProfileParent extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        Object prison = BRUserInfo.get()._new(
            PActivityThread.getUserId(), 
            "Prison", 
            BRUserInfo.get().FLAG_PRIMARY()
        );
        return prison;
    }
}
```

**使用场景**:
- 虚拟应用查询用户信息时
- 多用户环境管理时

**注意事项**:
- 返回虚拟用户信息，支持多用户隔离

### IAccountManagerProxy
**功能**: Hook `IAccountManager` 服务，管理账户  
**作用**:
- 虚拟化账户信息
- 拦截账户查询和操作
- 管理账户权限

### GoogleAccountManagerProxy
**功能**: Hook Google 账户管理器  
**作用**:
- 虚拟化 Google 账户
- 拦截 Google 账户相关操作
- 处理 Google 服务认证

### AuthenticationProxy
**功能**: Hook 认证相关操作  
**作用**:
- 虚拟化认证流程
- 拦截认证请求

---

## 设备标识和硬件服务代理

### AndroidIdProxy

**功能**: Hook `Settings.Secure.ANDROID_ID` 获取，为每个虚拟应用提供独立的 Android ID

**Hook 的方法**: `Settings.Secure.getString()` (当查询 `ANDROID_ID` 时)

**实现原理**:
- Hook `Settings.Secure.getString()` 方法
- 检测是否在查询 `ANDROID_ID`
- 如果原始 Android ID 无效（null、"0"、空字符串），生成虚拟 Android ID
- 每个虚拟应用获得独立的 Android ID

**Hook 的方法**:
- `getString()`: 当查询 `Settings.Secure.ANDROID_ID` 时拦截

**代码示例**:
```java
@ProxyMethod("getAndroidId")
public static class GetAndroidId extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        try {
            Object result = method.invoke(who, args);
            // 如果 Android ID 无效，生成虚拟 ID
            if (result == null || "0".equals(result.toString()) || 
                "".equals(result.toString())) {
                return generateMockAndroidId();
            }
            return result;
        } catch (Exception e) {
            return generateMockAndroidId();
        }
    }
    
    private String generateMockAndroidId() {
        // 基于包名和用户 ID 生成唯一的 Android ID
        String packageName = PActivityThread.getAppPackageName();
        int userId = PActivityThread.getUserId();
        // 生成 16 位十六进制字符串
        return generateHexId(packageName, userId);
    }
}
```

**使用场景**:
- Google Play Store 验证设备时
- 应用需要设备唯一标识时
- 广告 ID 生成时

**注意事项**:
- 每个虚拟应用有独立的 Android ID
- 解决 Google Play Store 的 Android ID 验证问题
- 确保 Android ID 格式正确（16 位十六进制）

### DeviceIdProxy
**功能**: Hook 设备 ID 获取  
**作用**:
- 虚拟化设备标识符（IMEI、Serial 等）
- 拦截设备 ID 查询
- 为虚拟应用提供独立设备 ID

### IDeviceIdentifiersPolicyProxy
**功能**: Hook `IDeviceIdentifiersPolicy` 服务，管理设备标识符策略  
**作用**:
- 虚拟化设备标识符策略
- 拦截设备标识符访问控制

### IPowerManagerProxy
**功能**: Hook `IPowerManager` 服务，管理电源  
**作用**:
- 虚拟化电源管理
- 拦截电源状态查询
- 管理唤醒锁

### IVibratorServiceProxy
**功能**: Hook `IVibratorService` 服务，管理震动  
**作用**:
- 虚拟化震动功能
- 拦截震动请求

### IFingerprintManagerProxy
**功能**: Hook `IFingerprintManager` 服务，管理指纹  
**作用**:
- 虚拟化指纹识别
- 拦截指纹相关操作

### IContextHubServiceProxy
**功能**: Hook `IContextHubService` 服务，管理上下文中心  
**作用**:
- 虚拟化上下文中心服务
- 拦截传感器融合数据

---

## WebView 和浏览器代理

### IWebViewUpdateServiceProxy
**功能**: Hook `IWebViewUpdateService` 服务，管理 WebView 更新  
**作用**:
- 虚拟化 WebView 更新服务
- 拦截 WebView 版本查询

### WebViewProxy

**功能**: Hook `WebView` 类，处理 WebView 数据目录冲突和初始化问题

**Hook 的类**: `android.webkit.WebView`

**实现原理**:
- Hook WebView 的构造函数和关键方法
- 为每个虚拟应用和进程创建独立的 WebView 数据目录
- 设置系统属性 (`webview.data.dir`, `webview.cache.dir` 等)
- 配置 WebView 设置以提高兼容性

**Hook 的方法**:

1. **`<init>()` (构造函数)**:
   - 创建唯一的 WebView 数据目录：`{dataDir}/webview_{userId}_{pid}`
   - 设置系统属性指向该目录
   - 配置 WebView 设置（JavaScript、DOM Storage 等）
   - 如果创建失败，创建备用 WebView

2. **`setDataDirectorySuffix()`**:
   - 为每个虚拟应用和进程创建唯一的后缀
   - 格式：`{suffix}_{userId}_{pid}`

3. **`getDataDirectory()`**:
   - 返回虚拟应用专用的数据目录
   - 确保目录存在

4. **`getInstance()` (WebViewDatabase)**:
   - 设置唯一的数据库路径
   - 格式：`{dataDir}/webview_db_{userId}_{pid}`

5. **`loadUrl()`**:
   - 处理文件 URL 等特殊情况

**代码示例**:
```java
@ProxyMethod("<init>")
public static class Constructor extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        Context context = (Context) args[0];
        String packageName = context.getPackageName();
        String userId = String.valueOf(PActivityThread.getUserId());
        
        // 创建唯一的数据目录
        String uniqueDataDir = context.getApplicationInfo().dataDir + 
            "/webview_" + userId + "_" + android.os.Process.myPid();
        File dataDir = new File(uniqueDataDir);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        // 设置系统属性
        System.setProperty("webview.data.dir", uniqueDataDir);
        System.setProperty("webview.cache.dir", uniqueDataDir + "/cache");
        System.setProperty("webview.cookies.dir", uniqueDataDir + "/cookies");
        
        // 继续正常构造
        WebView webView = (WebView) method.invoke(who, args);
        configureWebView(webView, context);
        return webView;
    }
}
```

**使用场景**:
- 虚拟应用使用 WebView 时
- 多个虚拟应用同时使用 WebView 时
- WebView 数据目录冲突时

**注意事项**:
- 每个虚拟应用和进程都有独立的 WebView 数据目录
- 避免 WebView 数据目录冲突导致的崩溃
- 需要确保目录权限正确

### WebViewFactoryProxy
**功能**: Hook `WebViewFactory` 类  
**作用**:
- 虚拟化 WebView 工厂
- 拦截 WebView 创建逻辑

---

## 数据库和存储代理

### SQLiteDatabaseProxy
**功能**: Hook `SQLiteDatabase` 类  
**作用**:
- 虚拟化数据库路径
- 重定向数据库文件到虚拟目录
- 拦截数据库操作

### LevelDbProxy
**功能**: Hook LevelDB 数据库操作  
**作用**:
- 虚拟化 LevelDB 数据库
- 重定向数据库文件路径

---

## 安全和管理服务代理

### IWindowManagerProxy

**功能**: Hook `IWindowManager` 服务，管理窗口和窗口会话

**Hook 的服务**: `IWindowManager` (通过 `ServiceManager.getService("window")`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 `IWindowManager` 的调用
- 替换系统服务 `"window"`
- 清空 `WindowManagerGlobal.sWindowManagerService`
- 拦截 `openSession()` 创建窗口会话，并 Hook 会话接口

**Hook 的方法**:

1. **窗口会话**:
   - `openSession()`: 打开窗口会话
     - 创建 `IWindowSessionProxy` 代理会话
     - Hook 会话接口，拦截窗口操作

**代码示例**:
```java
@ProxyMethod("openSession")
public static class OpenSession extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        IInterface session = (IInterface) method.invoke(who, args);
        IWindowSessionProxy proxy = new IWindowSessionProxy(session);
        proxy.inject();
        return proxy.getProxyInvocation();
    }
}
```

**使用场景**:
- 虚拟应用创建窗口时
- 窗口布局和显示时

**注意事项**:
- 窗口会话会被代理，确保窗口操作正常

### IAppWidgetManagerProxy
**功能**: Hook `IAppWidgetManager` 服务，管理应用小部件  
**作用**:
- 虚拟化应用小部件
- 拦截小部件操作

### ILauncherAppsProxy
**功能**: Hook `ILauncherApps` 服务，管理启动器应用  
**作用**:
- 虚拟化启动器应用查询
- 拦截应用列表查询

### IShortcutManagerProxy
**功能**: Hook `IShortcutManager` 服务，管理快捷方式  
**作用**:
- 虚拟化应用快捷方式
- 拦截快捷方式操作

### IJobServiceProxy
**功能**: Hook `IJobService` 服务，管理后台任务  
**作用**:
- 虚拟化 JobScheduler 任务
- 拦截后台任务调度

### IAccessibilityManagerProxy
**功能**: Hook `IAccessibilityManager` 服务，管理无障碍服务  
**作用**:
- 虚拟化无障碍服务
- 拦截无障碍服务查询

### IDevicePolicyManagerProxy
**功能**: Hook `IDevicePolicyManager` 服务，管理设备策略  
**作用**:
- 虚拟化设备策略管理
- 拦截策略查询

### IPersistentDataBlockServiceProxy
**功能**: Hook `IPersistentDataBlockService` 服务，管理持久数据块  
**作用**:
- 虚拟化持久数据块服务
- 拦截数据块操作

### ISystemUpdateProxy
**功能**: Hook `ISystemUpdate` 服务，管理系统更新  
**作用**:
- 虚拟化系统更新服务
- 拦截更新相关操作

### IAutofillManagerProxy
**功能**: Hook `IAutofillManager` 服务，管理自动填充  
**作用**:
- 虚拟化自动填充服务
- 拦截自动填充请求

### IGraphicsStatsProxy
**功能**: Hook `IGraphicsStats` 服务，管理图形统计  
**作用**:
- 虚拟化图形性能统计
- 拦截图形统计查询

---

## MIUI 特定代理

### IMiuiSecurityManagerProxy

**功能**: Hook MIUI 安全管理器，绕过 MIUI 安全检查

**Hook 的服务**: `miui.security.ISecurityManager` (通过 `ServiceManager.getService("miui.security.SecurityManager")`)

**实现原理**:
- 仅在 MIUI/HyperOS 设备上启用
- 通过 `BinderInvocationStub` 拦截 `ISecurityManager` 的调用
- 替换系统服务 `"miui.security.SecurityManager"`
- 所有安全相关方法都返回允许/成功的结果

**启用条件**:
- `BuildCompat.isMIUI()` 返回 true
- 或设备制造商/品牌包含 "xiaomi"
- 或系统显示名称包含 "hyperos"

**Hook 的方法** (共 20+ 个):

1. **隐私管理**:
   - `setAppPrivacyStatus()`: 设置应用隐私状态（返回 true）
   - `getAppPrivacyStatus()`: 获取应用隐私状态（返回 0，允许）

2. **权限控制**:
   - `setAppPermissionControlOpen()`: 设置权限控制开启（返回 true）
   - `isAppPermissionControlOpen()`: 权限控制是否开启（返回 false，未开启）
   - `actuallyCheckPermission()`: 实际权限检查（返回 0，授予）

3. **唤醒管理**:
   - `setWakeUpTime()`: 设置唤醒时间（返回 true）
   - `getWakeUpTime()`: 获取唤醒时间（返回 0L）
   - `setTrackWakeUp()`: 设置跟踪唤醒（返回 true）
   - `isTrackWakeUp()`: 是否跟踪唤醒（返回 false）

4. **游戏模式**:
   - `setGameMode()`: 设置游戏模式（返回 true）
   - `isGameMode()`: 是否游戏模式（返回 false）

5. **应用控制**:
   - `isAllowStartActivity()`: 是否允许启动 Activity（返回 true，始终允许）
   - `setAppRunningControlEnabled()`: 设置运行控制启用（返回 true）
   - `getAppRunningControlEnabled()`: 运行控制是否启用（返回 false，未启用）
   - `checkAccessControl()`: 检查访问控制（返回 true，允许）

6. **其他**:
   - `pushNewNotification()`: 推送新通知（返回 true）
   - `saveIcon()`: 保存图标（返回 true）
   - `getIcon()`: 获取图标（返回 null）
   - `isValidDevice()`: 设备是否有效（返回 true）
   - `checkSmsBlocked()`: 检查短信是否被阻止（返回 false，未阻止）
   - `setCurrentUserId()`: 设置当前用户 ID（返回 true）
   - `getCurrentUserId()`: 获取当前用户 ID（返回 0）

**代码示例**:
```java
@ProxyMethod("isAllowStartActivity")
public static class IsAllowStartActivity extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        Logger.d(TAG, "IsAllowStartActivity: Allowing activity start");
        return true; // 始终允许启动 Activity
    }
}
```

**使用场景**:
- MIUI/HyperOS 设备上运行虚拟应用时
- MIUI 安全检查阻止应用运行时
- 权限控制导致应用无法正常工作时

**注意事项**:
- 仅在 MIUI/HyperOS 设备上启用
- 所有安全检查都返回允许/成功
- 确保虚拟应用在 MIUI 设备上正常运行

### IXiaomiAttributionSourceProxy
**功能**: Hook 小米 AttributionSource 相关操作  
**作用**:
- 处理小米设备的 AttributionSource
- 防止 MIUI 设备上的崩溃

### IXiaomiSettingsProxy
**功能**: Hook 小米设置相关操作  
**作用**:
- 虚拟化小米设置
- 拦截小米特定设置查询

### IXiaomiMiuiServicesProxy
**功能**: Hook 小米 MIUI 服务  
**作用**:
- 虚拟化 MIUI 服务
- 拦截 MIUI 特定服务调用

---

## 其他工具类代理

### SystemLibraryProxy
**功能**: Hook 系统库加载  
**作用**:
- 虚拟化系统库路径
- 拦截库加载操作

### ReLinkerProxy
**功能**: Hook ReLinker 库加载器  
**作用**:
- 虚拟化 ReLinker 操作
- 拦截库重链接

### WorkManagerProxy
**功能**: Hook WorkManager 后台任务管理器  
**作用**:
- 虚拟化 WorkManager 任务
- 拦截后台任务调度

### ClassLoaderProxy

**功能**: Hook `ClassLoader` 类加载器，处理 DEX 文件损坏和类加载失败

**Hook 的类**: `java.lang.ClassLoader` 及其子类

**实现原理**:
- Hook `ClassLoader` 的关键方法（`loadClass`, `findClass`, `forName`）
- Hook `DexFile` 和 `DexPathList` 的操作
- 使用类缓存避免重复加载失败
- 提供备用 ClassLoader 链（宿主、系统、Boot）
- 检测和恢复损坏的 DEX 文件

**Hook 的方法**:

1. **类加载方法**:
   - `loadClass()`: 加载类
     - 检查缓存
     - 检测问题类（Kotlin 测试类、MediaTek 性能类等）
     - 尝试原始方法
     - 失败时尝试备用 ClassLoader
   - `findClass()`: 查找类（同上）
   - `forName()`: 通过类名获取类（同上）

2. **DEX 文件操作**:
   - `openDexFile()`: 打开 DEX 文件
     - 验证 DEX 文件有效性
     - 如果损坏，尝试恢复
     - 如果恢复失败，返回 null 防止崩溃
   - `loadDexFile()`: 加载 DEX 文件
     - 捕获 "classes.dex: Entry not found" 错误
     - 使用 `DexFileRecovery` 恢复损坏的文件
     - 创建备用 DexPathList

**问题类检测**:
- Kotlin 协程测试类：`kotlinx.coroutines.test.*`
- MediaTek 性能类：`com.mediatek.perfservice.*`
- DataStore Protobuf 类：`androidx.datastore.preferences.protobuf.*`
- Robolectric 测试框架：`org.robolectric.*`

**备用 ClassLoader 链**:
1. 宿主应用 ClassLoader
2. 系统 ClassLoader
3. Boot ClassLoader

**代码示例**:
```java
@ProxyMethod("loadClass")
public static class LoadClass extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        String className = (String) args[0];
        
        // 检查缓存
        if (sClassCache.containsKey(className)) {
            return sClassCache.get(className);
        }
        
        // 检测问题类
        if (isProblematicClass(className)) {
            return null;
        }
        
        try {
            // 尝试原始方法
            Class<?> result = (Class<?>) method.invoke(who, args);
            sClassCache.put(className, result);
            return result;
        } catch (Exception e) {
            // 尝试备用 ClassLoader
            Class<?> fallback = tryFallbackClassLoaders(className);
            if (fallback != null) {
                sClassCache.put(className, fallback);
                return fallback;
            }
            return null;
        }
    }
}
```

**使用场景**:
- DEX 文件损坏导致类加载失败时
- "classes.dex: Entry not found" 错误时
- ClassNotFoundException 时
- 某些问题类导致崩溃时

**注意事项**:
- 使用类缓存提高性能
- 问题类返回 null，避免崩溃
- DEX 文件损坏时会尝试恢复
- 如果所有方法都失败，返回 null 而不是崩溃

### ApkAssetsProxy

**功能**: Hook `ApkAssets` 资源加载，阻止有问题的资源路径

**Hook 的类**: `android.content.res.ApkAssets` (静态方法)

**实现原理**:
- Hook `ApkAssets` 的静态方法
- 检测资源路径，阻止有问题的路径
- 防止资源加载导致的崩溃

**Hook 的方法**:

1. **`loadOverlayFromPath()`**:
   - 检测路径是否包含问题关键词
   - 如果包含，抛出 `RuntimeException` 阻止加载
   - 否则正常加载

2. **`nativeLoad()`**:
   - 检测路径是否包含问题关键词
   - 如果包含，抛出 `RuntimeException` 阻止加载
   - 否则正常加载

**阻止的路径关键词**:
- `resource-cache`
- `@idmap`
- `.frro`
- `systemui`
- `data@resource-cache@`

**代码示例**:
```java
@ProxyMethod("loadOverlayFromPath")
public static class LoadOverlayFromPath extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        String path = (String) args[0];
        
        // 阻止有问题的资源路径
        if (path != null && (path.contains("resource-cache") || 
                            path.contains("@idmap") || 
                            path.contains(".frro") ||
                            path.contains("systemui") ||
                            path.contains("data@resource-cache@"))) {
            Log.d(TAG, "Blocking problematic overlay path: " + path);
            throw new RuntimeException("Blocked problematic overlay path: " + path);
        }
        
        return method.invoke(who, args);
    }
}
```

**使用场景**:
- 系统尝试加载有问题的资源覆盖层时
- 资源缓存路径导致崩溃时

**注意事项**:
- 阻止某些路径可能导致资源无法加载，但避免崩溃
- 需要平衡功能性和稳定性

### ResourcesManagerProxy

**功能**: Hook `ResourcesManager` 资源管理器，阻止有问题的资源加载

**Hook 的类**: `android.app.ResourcesManager` (静态方法)

**实现原理**:
- Hook `ResourcesManager` 的静态方法
- 检测资源路径，阻止有问题的路径
- 防止资源加载导致的崩溃

**Hook 的方法**:

1. **`loadApkAssets()`**:
   - 检测路径是否包含问题关键词
   - 如果包含，返回 null 阻止加载
   - 否则正常加载

2. **`loadOverlayFromPath()`**:
   - 检测路径是否包含问题关键词
   - 如果包含，返回 null 阻止加载
   - 否则正常加载

**阻止的路径关键词**:
- `resource-cache`
- `@idmap`
- `.frro`
- `systemui`
- `data@resource-cache@`

**代码示例**:
```java
@ProxyMethod("loadApkAssets")
public static class LoadApkAssets extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        String path = (String) args[0];
        if (path != null && (path.contains("resource-cache") || 
                            path.contains("@idmap") || 
                            path.contains(".frro"))) {
            Log.d(TAG, "Blocking problematic ApkAssets load: " + path);
            return null; // 阻止加载
        }
        return method.invoke(who, args);
    }
}
```

**使用场景**:
- 系统尝试加载有问题的资源时
- 资源缓存路径导致崩溃时

**注意事项**:
- 与 `ApkAssetsProxy` 类似，但作用于 `ResourcesManager`
- 阻止某些路径可能导致资源无法加载，但避免崩溃

### ISettingsProviderProxy
**功能**: Hook `ISettingsProvider` 服务，管理设置  
**作用**:
- 虚拟化系统设置
- 拦截设置查询和修改

### ISettingsSystemProxy
**功能**: Hook `ISettings.System` 设置  
**作用**:
- 虚拟化系统设置
- 拦截系统设置访问

### FeatureFlagUtilsProxy
**功能**: Hook 功能标志工具类  
**作用**:
- 虚拟化功能标志
- 拦截功能标志查询

### ITelephonyManagerProxy
**功能**: Hook `ITelephonyManager` 服务，管理电话  
**作用**:
- 虚拟化电话服务
- 拦截电话状态查询
- 管理电话权限

### ITelephonyRegistryProxy
**功能**: Hook `ITelephonyRegistry` 服务，管理电话注册  
**作用**:
- 虚拟化电话注册
- 拦截电话状态监听

### IPhoneSubInfoProxy
**功能**: Hook `IPhoneSubInfo` 服务，管理电话订阅信息  
**作用**:
- 虚拟化电话订阅信息
- 拦截 IMEI、Serial 等查询

### IAttributionSourceProxy
**功能**: Hook `AttributionSource` 属性源  
**作用**:
- 虚拟化属性源
- 拦截权限属性查询

### IContentProviderProxy

**功能**: Hook `IContentProvider` 接口，处理 AttributionSource UID 问题（Android 12+）

**Hook 的接口**: `android.content.IContentProvider` (全局 Hook)

**实现原理**:
- 通过 `ClassInvocationStub` 全局 Hook `IContentProvider` 接口
- 在调用内容提供者方法前，修复 `AttributionSource` 中的 UID
- 防止 Android 12+ 的 UID 强制检查导致的崩溃
- 使用 `AttributionSourceUtils.fixAttributionSourceInArgs()` 修复参数

**Hook 的方法**:

1. **数据操作**:
   - `query()`: 查询数据
     - 修复 `AttributionSource` 后调用原始方法
     - 错误时返回 null Cursor
   - `insert()`: 插入数据
     - 修复 `AttributionSource` 后调用原始方法
     - 错误时返回 null URI
   - `update()`: 更新数据
     - 修复 `AttributionSource` 后调用原始方法
     - 错误时返回 0
   - `delete()`: 删除数据
     - 修复 `AttributionSource` 后调用原始方法
     - 错误时返回 0

2. **文件操作**:
   - `openFile()`: 打开文件
     - 修复 `AttributionSource` 后调用原始方法
     - 错误时返回 null ParcelFileDescriptor
   - `openAssetFile()`: 打开资源文件
   - `openTypedAssetFile()`: 打开类型化资源文件

3. **其他**:
   - `call()`: 调用自定义方法
   - `getStreamTypes()`: 获取流类型
   - `canonicalize()`: 规范化 URI
   - `uncanonicalize()`: 反规范化 URI

**代码示例**:
```java
@ProxyMethod("query")
public static class Query extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        try {
            // 修复 AttributionSource 中的 UID
            AttributionSourceUtils.fixAttributionSourceInArgs(args);
            return method.invoke(who, args);
        } catch (Exception e) {
            Logger.w(TAG, "Error in query hook: " + e.getMessage());
            return null; // 返回 null Cursor 防止崩溃
        }
    }
}
```

**使用场景**:
- Android 12+ 设备上访问内容提供者时
- AttributionSource UID 不匹配导致崩溃时
- 虚拟应用访问 MediaStore、Contacts 等内容提供者时

**注意事项**:
- 仅在 Android 12+ 需要此代理
- 修复 UID 确保权限检查通过
- 错误时返回安全默认值

### GmsProxy

**功能**: Hook Google Mobile Services (GMS) 相关操作，处理 GMS 特定问题

**Hook 的服务**: `com.google.android.gms.common.api.internal.IGmsServiceBroker` (通过 `ServiceManager.getService("gms")`)

**实现原理**:
- 通过 `BinderInvocationStub` 拦截 GMS 服务代理的调用
- 替换系统服务 `"gms"`
- 处理包名验证、认证、令牌等问题
- 防止 GMS 相关崩溃

**Hook 的方法**:

1. **服务获取**:
   - `getService()`: 获取 GMS 服务
     - 如果调用包名是 `"com.google.android.gms"`，替换为宿主包名
     - 错误时返回 null 防止崩溃
   - `getServiceBroker()`: 获取服务代理
     - 错误时返回 null

2. **认证和账户**:
   - `authenticate()`: 认证
     - 错误时返回虚拟认证结果（Bundle）
   - `getAccount()`: 获取账户
     - 错误时返回 null
   - `getToken()`: 获取令牌
     - 错误时返回虚拟令牌：`"mock_gms_token_{timestamp}"`

3. **令牌管理**:
   - `invalidateToken()`: 使令牌失效
     - 错误时返回 null（忽略）
   - `clearToken()`: 清除令牌
     - 错误时返回 null（忽略）

**代码示例**:
```java
@ProxyMethod("getService")
public static class GetService extends Settings.MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) {
        if (args != null && args.length > 0) {
            String callingPackage = (String) args[0];
            if ("com.google.android.gms".equals(callingPackage)) {
                // 替换为宿主包名
                args[0] = PrisonCore.getPackageName();
                Logger.d(TAG, "Fixed calling package to " + PrisonCore.getPackageName());
            }
        }
        try {
            return method.invoke(who, args);
        } catch (Exception e) {
            Logger.e(TAG, "Error in getService", e);
            return null; // 防止崩溃
        }
    }
}
```

**使用场景**:
- 虚拟应用使用 Google Play 服务时
- GMS 认证失败时
- 令牌获取失败时

**注意事项**:
- 处理 GMS 特定的包名验证问题
- 错误时返回安全默认值，避免崩溃
- 虚拟令牌可能无法用于实际认证

---

## 代理类分类总结

### 核心代理（Critical Injectors）
这些代理类是虚拟化框架的核心，必须正常工作：

1. **IActivityManagerProxy** - Activity 管理
2. **IPackageManagerProxy** - 包管理
3. **HCallbackProxy** - 消息循环拦截
4. **IContentProviderProxy** - 内容提供者
5. **WebViewProxy** - WebView 组件

### 功能代理（Functional Proxies）
按功能分类的代理：

- **网络**: `IConnectivityManagerProxy`, `IWifiManagerProxy`, `IVpnManagerProxy`
- **存储**: `IStorageManagerProxy`, `FileSystemProxy`, `SQLiteDatabaseProxy`
- **媒体**: `IAudioServiceProxy`, `MediaRecorderProxy`, `AudioRecordProxy`
- **位置**: `ILocationManagerProxy`, `ISystemSensorManagerProxy`
- **通知**: `INotificationManagerProxy`, `IAlarmManagerProxy`
- **用户**: `IUserManagerProxy`, `IAccountManagerProxy`
- **设备**: `AndroidIdProxy`, `DeviceIdProxy`, `IPowerManagerProxy`

### 兼容性代理（Compatibility Proxies）
针对特定设备或系统的代理：

- **MIUI**: `IMiuiSecurityManagerProxy`, `IXiaomiAttributionSourceProxy`, `IXiaomiSettingsProxy`, `IXiaomiMiuiServicesProxy`
- **工具**: `ClassLoaderProxy`, `ApkAssetsProxy`, `ResourcesManagerProxy`

---

## 代理类工作流程

```
应用调用系统服务
    ↓
代理类拦截调用 (Hook)
    ↓
检查虚拟环境状态
    ↓
重定向到虚拟服务
    ├─ 查询虚拟数据
    ├─ 修改参数/返回值
    └─ 权限检查
    ↓
返回虚拟化结果
```

---

## 注意事项

1. **注入顺序**: 某些代理类有依赖关系，需要按顺序注入
2. **版本兼容**: 不同 Android 版本可能需要不同的代理实现
3. **MIUI 兼容**: MIUI 设备需要额外的代理类来防止崩溃
4. **性能影响**: 代理类会增加系统调用开销，但影响很小
5. **错误处理**: 代理类失败不会导致系统崩溃，只会记录日志

---

## 相关文档

- [ActivityStack 工作原理](./ActivityStack工作原理.md)
- [ActivityManagerService 工作原理](./ActivityManagerService工作原理.md)
- [HCallbackProxy 详细说明](./README.md#hcallbackproxy)

---

---

## 完整代理类列表（按注册顺序）

以下是 `InjectorManager` 中注册的所有代理类（按第 103-187 行的顺序）：

### 1. IDisplayManagerProxy
**详细说明**: 见上文 [核心系统服务代理 - IDisplayManagerProxy](#idisplaymanagerproxy)

### 2. OsStub
**详细说明**: 见上文 [核心系统服务代理 - OsStub](#osstub)

### 3. IActivityManagerProxy
**详细说明**: 见上文 [Activity 和任务管理代理 - IActivityManagerProxy](#iactivitymanagerproxy)

### 4. IPackageManagerProxy
**详细说明**: 见上文 [包和权限管理代理 - IPackageManagerProxy](#ipackagemanagerproxy)

### 5. ITelephonyManagerProxy
**功能**: Hook `ITelephonyManager` 服务，管理电话服务  
**Hook 的方法**: 电话状态查询、IMEI/Serial 查询等  
**作用**: 虚拟化电话服务，拦截电话状态查询，管理电话权限

### 6. HCallbackProxy
**详细说明**: 见上文 [Activity 和任务管理代理 - HCallbackProxy](#hcallbackproxy)

### 7. IAppOpsManagerProxy
**功能**: Hook `IAppOpsManager` 服务，管理应用操作权限  
**Hook 的方法**: AppOps 权限检查、操作权限查询等  
**作用**: 虚拟化 AppOps 权限检查，拦截敏感操作（位置、相机等），管理虚拟应用的操作权限

### 8. INotificationManagerProxy
**详细说明**: 见上文 [通知和提醒服务代理 - INotificationManagerProxy](#inotificationmanagerproxy)

### 9. IAlarmManagerProxy
**功能**: Hook `IAlarmManager` 服务，管理定时任务  
**Hook 的方法**: 设置闹钟、定时器等  
**作用**: 虚拟化闹钟和定时器，拦截定时任务设置，管理虚拟应用的定时任务

### 10. IAppWidgetManagerProxy
**功能**: Hook `IAppWidgetManager` 服务，管理应用小部件  
**Hook 的方法**: 小部件创建、更新、删除等  
**作用**: 虚拟化应用小部件，拦截小部件操作

### 11. ContentServiceStub
**详细说明**: 见上文 [核心系统服务代理 - ContentServiceStub](#contentservicestub)

### 12. IWindowManagerProxy
**详细说明**: 见上文 [安全和管理服务代理 - IWindowManagerProxy](#iwindowmanagerproxy)

### 13. IUserManagerProxy
**详细说明**: 见上文 [用户和账户管理代理 - IUserManagerProxy](#iusermanagerproxy)

### 14. RestrictionsManagerStub
**功能**: Hook `RestrictionsManager`，管理应用限制  
**作用**: 虚拟化应用限制，拦截限制查询

### 15. IMediaSessionManagerProxy
**功能**: Hook `IMediaSessionManager` 服务，管理媒体会话  
**Hook 的方法**: 媒体会话创建、控制等  
**作用**: 虚拟化媒体会话，拦截媒体控制请求

### 16. IAudioServiceProxy
**详细说明**: 见上文 [媒体和音频服务代理 - IAudioServiceProxy](#iaudioserviceproxy)

### 17. ISensorPrivacyManagerProxy
**功能**: Hook `ISensorPrivacyManager` 服务，管理传感器隐私  
**Hook 的方法**: 传感器隐私设置查询  
**作用**: 虚拟化传感器隐私设置，拦截传感器隐私检查

### 18. ContentResolverProxy
**详细说明**: 见上文 [核心系统服务代理 - ContentResolverProxy](#contentresolverproxy)

### 19. IWebViewUpdateServiceProxy
**功能**: Hook `IWebViewUpdateService` 服务，管理 WebView 更新  
**Hook 的方法**: WebView 版本查询等  
**作用**: 虚拟化 WebView 更新服务，拦截 WebView 版本查询

### 20. SystemLibraryProxy
**功能**: Hook 系统库加载  
**Hook 的方法**: 库加载操作  
**作用**: 虚拟化系统库路径，拦截库加载操作

### 21. ReLinkerProxy
**功能**: Hook ReLinker 库加载器  
**Hook 的方法**: 库重链接操作  
**作用**: 虚拟化 ReLinker 操作，拦截库重链接

### 22. WebViewProxy
**详细说明**: 见上文 [WebView 和浏览器代理 - WebViewProxy](#webviewproxy)

### 23. WebViewFactoryProxy
**功能**: Hook `WebViewFactory` 类  
**Hook 的方法**: WebView 创建逻辑  
**作用**: 虚拟化 WebView 工厂，拦截 WebView 创建逻辑

### 24. WorkManagerProxy
**功能**: Hook WorkManager 后台任务管理器  
**Hook 的方法**: 后台任务调度  
**作用**: 虚拟化 WorkManager 任务，拦截后台任务调度

### 25. MediaRecorderProxy
**功能**: Hook `MediaRecorder` 类，管理媒体录制  
**Hook 的方法**: 录制相关操作  
**作用**: 虚拟化媒体录制，拦截录制权限检查

### 26. AudioRecordProxy
**功能**: Hook `AudioRecord` 类，管理音频录制  
**Hook 的方法**: 录音相关操作  
**作用**: 虚拟化音频录制，拦截录音权限检查，管理录音设备访问

### 27. IMiuiSecurityManagerProxy
**详细说明**: 见上文 [MIUI 特定代理 - IMiuiSecurityManagerProxy](#imiuisecuritymanagerproxy)

### 28. ISettingsProviderProxy
**功能**: Hook `ISettingsProvider` 服务，管理设置  
**Hook 的方法**: 设置查询和修改  
**作用**: 虚拟化系统设置，拦截设置查询和修改

### 29. FeatureFlagUtilsProxy
**功能**: Hook 功能标志工具类  
**Hook 的方法**: 功能标志查询  
**作用**: 虚拟化功能标志，拦截功能标志查询

### 30. MediaRecorderClassProxy
**功能**: Hook `MediaRecorder` 类的静态方法  
**Hook 的方法**: MediaRecorder 静态调用  
**作用**: 拦截 MediaRecorder 的静态调用，虚拟化录制相关操作

### 31. SQLiteDatabaseProxy
**功能**: Hook `SQLiteDatabase` 类  
**Hook 的方法**: 数据库操作  
**作用**: 虚拟化数据库路径，重定向数据库文件到虚拟目录，拦截数据库操作

### 32. ClassLoaderProxy
**详细说明**: 见上文 [其他工具类代理 - ClassLoaderProxy](#classloaderproxy)

### 33. FileSystemProxy
**功能**: Hook 文件系统操作  
**Hook 的方法**: 文件路径操作  
**作用**: 虚拟化文件路径，重定向文件访问到虚拟目录，拦截文件系统调用

### 34. GmsProxy
**详细说明**: 见上文 [其他工具类代理 - GmsProxy](#gmsproxy)

### 35. LevelDbProxy
**功能**: Hook LevelDB 数据库操作  
**Hook 的方法**: LevelDB 操作  
**作用**: 虚拟化 LevelDB 数据库，重定向数据库文件路径

### 36. DeviceIdProxy
**功能**: Hook 设备 ID 获取  
**Hook 的方法**: 设备标识符查询（IMEI、Serial 等）  
**作用**: 虚拟化设备标识符，拦截设备 ID 查询，为虚拟应用提供独立设备 ID

### 37. GoogleAccountManagerProxy
**功能**: Hook Google 账户管理器  
**Hook 的方法**: Google 账户操作  
**作用**: 虚拟化 Google 账户，拦截 Google 账户相关操作，处理 Google 服务认证

### 38. AuthenticationProxy
**功能**: Hook 认证相关操作  
**Hook 的方法**: 认证流程  
**作用**: 虚拟化认证流程，拦截认证请求

### 39. AndroidIdProxy
**详细说明**: 见上文 [设备标识和硬件服务代理 - AndroidIdProxy](#androididproxy)

### 40. AudioPermissionProxy
**功能**: Hook 音频权限检查  
**Hook 的方法**: 音频权限相关操作  
**作用**: 虚拟化音频权限，自动授予音频相关权限

### 41. ILocationManagerProxy
**详细说明**: 见上文 [位置和传感器服务代理 - ILocationManagerProxy](#ilocationmanagerproxy)

### 42. IStorageManagerProxy
**详细说明**: 见上文 [存储和文件系统代理 - IStorageManagerProxy](#istoragemanagerproxy)

### 43. ILauncherAppsProxy
**功能**: Hook `ILauncherApps` 服务，管理启动器应用  
**Hook 的方法**: 应用列表查询等  
**作用**: 虚拟化启动器应用查询，拦截应用列表查询

### 44. IJobServiceProxy (第一个)
**功能**: Hook `IJobService` 服务，管理后台任务  
**Hook 的方法**: JobScheduler 任务调度  
**作用**: 虚拟化 JobScheduler 任务，拦截后台任务调度

### 45. IAccessibilityManagerProxy
**功能**: Hook `IAccessibilityManager` 服务，管理无障碍服务  
**Hook 的方法**: 无障碍服务查询  
**作用**: 虚拟化无障碍服务，拦截无障碍服务查询

### 46. ITelephonyRegistryProxy
**功能**: Hook `ITelephonyRegistry` 服务，管理电话注册  
**Hook 的方法**: 电话状态监听  
**作用**: 虚拟化电话注册，拦截电话状态监听

### 47. IDevicePolicyManagerProxy
**功能**: Hook `IDevicePolicyManager` 服务，管理设备策略  
**Hook 的方法**: 策略查询  
**作用**: 虚拟化设备策略管理，拦截策略查询

### 48. IAccountManagerProxy
**功能**: Hook `IAccountManager` 服务，管理账户  
**Hook 的方法**: 账户查询和操作  
**作用**: 虚拟化账户信息，拦截账户查询和操作，管理账户权限

### 49. IConnectivityManagerProxy
**详细说明**: 见上文 [网络和连接服务代理 - IConnectivityManagerProxy](#iconnectivitymanagerproxy)

### 50. IDnsResolverProxy
**功能**: Hook `IDnsResolver` 服务，管理 DNS 解析  
**Hook 的方法**: DNS 查询  
**作用**: 虚拟化 DNS 解析，拦截 DNS 查询请求

### 51. IAttributionSourceProxy
**功能**: Hook `AttributionSource` 属性源  
**Hook 的方法**: 属性源相关操作  
**作用**: 虚拟化属性源，拦截权限属性查询

### 52. IContentProviderProxy
**详细说明**: 见上文 [安全和管理服务代理 - IContentProviderProxy](#icontentproviderproxy)

### 53. ISettingsSystemProxy
**功能**: Hook `ISettings.System` 设置  
**Hook 的方法**: 系统设置访问  
**作用**: 虚拟化系统设置，拦截系统设置访问

### 54. ISystemSensorManagerProxy
**功能**: Hook `ISystemSensorManager` 服务，管理传感器  
**Hook 的方法**: 传感器数据查询  
**作用**: 虚拟化传感器访问，拦截传感器数据查询，管理传感器权限

### 55. IXiaomiAttributionSourceProxy
**功能**: Hook 小米 AttributionSource 相关操作  
**Hook 的方法**: AttributionSource 操作  
**作用**: 处理小米设备的 AttributionSource，防止 MIUI 设备上的崩溃

### 56. IXiaomiSettingsProxy
**功能**: Hook 小米设置相关操作  
**Hook 的方法**: 小米设置查询  
**作用**: 虚拟化小米设置，拦截小米特定设置查询

### 57. IXiaomiMiuiServicesProxy
**功能**: Hook 小米 MIUI 服务  
**Hook 的方法**: MIUI 服务调用  
**作用**: 虚拟化 MIUI 服务，拦截 MIUI 特定服务调用

### 58. IPhoneSubInfoProxy
**功能**: Hook `IPhoneSubInfo` 服务，管理电话订阅信息  
**Hook 的方法**: IMEI、Serial 等查询  
**作用**: 虚拟化电话订阅信息，拦截 IMEI、Serial 等查询

### 59. IMediaRouterServiceProxy
**功能**: Hook `IMediaRouterService` 服务，管理媒体路由  
**Hook 的方法**: 媒体投屏请求  
**作用**: 虚拟化媒体路由，拦截媒体投屏请求

### 60. IPowerManagerProxy
**功能**: Hook `IPowerManager` 服务，管理电源  
**Hook 的方法**: 电源状态查询、唤醒锁等  
**作用**: 虚拟化电源管理，拦截电源状态查询，管理唤醒锁

### 61. IContextHubServiceProxy
**功能**: Hook `IContextHubService` 服务，管理上下文中心  
**Hook 的方法**: 传感器融合数据  
**作用**: 虚拟化上下文中心服务，拦截传感器融合数据

### 62. IVibratorServiceProxy
**功能**: Hook `IVibratorService` 服务，管理震动  
**Hook 的方法**: 震动请求  
**作用**: 虚拟化震动功能，拦截震动请求

### 63. IPersistentDataBlockServiceProxy
**功能**: Hook `IPersistentDataBlockService` 服务，管理持久数据块  
**Hook 的方法**: 数据块操作  
**作用**: 虚拟化持久数据块服务，拦截数据块操作

### 64. IWifiManagerProxy
**功能**: Hook `IWifiManager` 服务，管理 WiFi  
**Hook 的方法**: WiFi 状态查询、连接等  
**作用**: 虚拟化 WiFi 连接，拦截 WiFi 状态查询，管理 WiFi 权限（仅在 Android 10 测试）

### 65. IWifiScannerProxy
**功能**: Hook `IWifiScanner` 服务，管理 WiFi 扫描  
**Hook 的方法**: WiFi 扫描请求  
**作用**: 虚拟化 WiFi 扫描功能，拦截 WiFi 扫描请求

### 66. ApkAssetsProxy
**详细说明**: 见上文 [其他工具类代理 - ApkAssetsProxy](#apkassetsproxy)

### 67. ResourcesManagerProxy
**详细说明**: 见上文 [其他工具类代理 - ResourcesManagerProxy](#resourcesmanagerproxy)

### 68. IVpnManagerProxy
**功能**: Hook `IVpnManager` 服务，管理 VPN  
**Hook 的方法**: VPN 连接操作  
**作用**: 虚拟化 VPN 连接，拦截 VPN 相关操作

### 69. IPermissionManagerProxy
**功能**: Hook `IPermissionManager` 服务，管理权限  
**Hook 的方法**: 权限授予和撤销  
**作用**: 虚拟化权限授予和撤销，拦截权限检查请求，管理虚拟应用的权限状态

### 70. IActivityTaskManagerProxy
**功能**: Hook `IActivityTaskManager` 服务（Android 10+），管理任务栈  
**Hook 的方法**: 任务栈操作  
**作用**: 管理 Activity 任务栈，处理任务切换和恢复，虚拟化任务记录

### 71. ISystemUpdateProxy
**功能**: Hook `ISystemUpdate` 服务，管理系统更新  
**Hook 的方法**: 更新相关操作  
**作用**: 虚拟化系统更新服务，拦截更新相关操作

### 72. IAutofillManagerProxy
**功能**: Hook `IAutofillManager` 服务，管理自动填充  
**Hook 的方法**: 自动填充请求  
**作用**: 虚拟化自动填充服务，拦截自动填充请求

### 73. IDeviceIdentifiersPolicyProxy
**功能**: Hook `IDeviceIdentifiersPolicy` 服务，管理设备标识符策略  
**Hook 的方法**: 设备标识符访问控制  
**作用**: 虚拟化设备标识符策略，拦截设备标识符访问控制

### 74. IStorageStatsManagerProxy
**功能**: Hook `IStorageStatsManager` 服务，管理存储统计  
**Hook 的方法**: 存储使用统计查询  
**作用**: 虚拟化存储使用统计，拦截存储空间查询

### 75. IShortcutManagerProxy
**功能**: Hook `IShortcutManager` 服务，管理快捷方式  
**Hook 的方法**: 快捷方式操作  
**作用**: 虚拟化应用快捷方式，拦截快捷方式操作

### 76. INetworkManagementServiceProxy
**功能**: Hook `INetworkManagementService` 服务，管理网络管理  
**Hook 的方法**: 网络配置请求  
**作用**: 虚拟化网络管理操作，拦截网络配置请求

### 77. IFingerprintManagerProxy
**功能**: Hook `IFingerprintManager` 服务，管理指纹  
**Hook 的方法**: 指纹相关操作  
**作用**: 虚拟化指纹识别，拦截指纹相关操作

### 78. IGraphicsStatsProxy
**功能**: Hook `IGraphicsStats` 服务，管理图形统计  
**Hook 的方法**: 图形性能统计查询  
**作用**: 虚拟化图形性能统计，拦截图形统计查询

### 79. IJobServiceProxy (第二个，重复)
**功能**: 同上 [IJobServiceProxy](#44-ijobserviceproxy-第一个)

---

## 代理类统计

- **总数量**: 85+ 个代理类
- **核心代理**: 5 个（必须正常工作）
- **功能代理**: 60+ 个（按功能分类）
- **兼容性代理**: 20+ 个（MIUI、工具类等）

---

## 更新日志

- 2024-02-02: 创建初始文档，列出所有代理类及其功能
- 2024-02-02: 添加详细说明，包括实现原理、Hook 方法、代码示例、使用场景和注意事项
