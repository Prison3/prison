# ActivityManagerService Internals

## Contents
1. [Overview](#overview)
2. [Core Architecture](#core-architecture)
3. [Multi-User Isolation](#multi-user-isolation)
4. [Main Functional Modules](#main-functional-modules)
5. [Workflow](#workflow)
6. [Key Features](#key-features)
7. [Class Relationship Diagram](#class-relationship-diagram)

---

## Overview

`ActivityManagerService` is the core system service in the virtualized environment. It manages
Activity, Service, and Broadcast in a multi-user context. Through user space isolation, it provides
an independent component management environment for each user.

### Main Responsibilities
- Manage Activity launch and lifecycle
- Manage Service start, bind, and lifecycle
- Manage Broadcast send and receive
- Manage ContentProvider acquisition
- Provide process initialization interfaces
- Manage IntentSender and PendingIntent

---

## Core Architecture

### Class Structure

```java
public class ActivityManagerService extends IPActivityManagerService.Stub 
                                   implements ISystemService {
    private static final ActivityManagerService sService = new ActivityManagerService();
    private final Map<Integer, UserSpace> mUserSpace = new HashMap<>();
    private final PackageManagerService mPms;
    private final BroadcastManager mBroadcastManager;
}
```

### Key Components

1. **UserSpace**: User space, providing isolated component management per user
2. **ActivityStack**: Activity stack manager
3. **ActiveServices**: Service manager
4. **BroadcastManager**: Broadcast manager
5. **ProcessManagerService**: Process manager

---

## Multi-User Isolation

### UserSpace Structure

```java
public class UserSpace {
    public final ActiveServices mActiveServices = new ActiveServices();
    public final ActivityStack mStack = new ActivityStack();
    public final Map<IBinder, PendingIntentRecord> mIntentSenderRecords = new HashMap<>();
}
```

**Composition**:
- `mActiveServices`: Manages Services for the user
- `mStack`: Manages the user's Activity stack
- `mIntentSenderRecords`: Manages PendingIntent records

### Getting UserSpace

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

**Mechanism**:
- One UserSpace per userId
- Created on first access, reused afterward
- Thread-safe via `synchronized`

---

## Main Functional Modules

### 1. Activity Management

#### Start Activity

```java
@Override
public void startActivity(Intent intent, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mStack) {
        userSpace.mStack.startActivityLocked(userId, intent, null, null, null, -1, -1, null);
    }
}
```

**Flow**:
1. Get the user space
2. Start Activity in the user space's ActivityStack

#### Activity Lifecycle Callbacks

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

**Supported callbacks**:
- `onActivityCreated()`: Activity created
- `onActivityResumed()`: Activity resumed
- `onActivityDestroyed()`: Activity destroyed
- `onFinishActivity()`: Activity finished

### 2. Service Management

#### Start Service

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

**Supported operations**:
- `startService()`: Start service
- `stopService()`: Stop service
- `bindService()`: Bind service
- `unbindService()`: Unbind service
- `onStartCommand()`: Handle service start command
- `onServiceUnbind()`: Handle service unbind
- `onServiceDestroy()`: Handle service destroy

#### Peek Service

```java
@Override
public IBinder peekService(Intent intent, String resolvedType, int userId) {
    UserSpace userSpace = getOrCreateSpaceLocked(userId);
    synchronized (userSpace.mActiveServices) {
        return userSpace.mActiveServices.peekService(intent, resolvedType, userId);
    }
}
```

### 3. Broadcast Management

#### Send Broadcast

```java
@Override
public Intent sendBroadcast(Intent intent, String resolvedType, int userId) {
    // Query receivers
    List<ResolveInfo> resolves = PackageManagerService.get()
        .queryBroadcastReceivers(intent, GET_META_DATA, resolvedType, userId);
    
    // Bind apps and prepare receivers
    for (ResolveInfo resolve : resolves) {
        ProcessRecord processRecord = ProcessManagerService.get()
            .findProcessRecord(resolve.activityInfo.packageName, 
                              resolve.activityInfo.processName, userId);
        if (processRecord == null) {
            continue;
        }
        processRecord.bActivityThread.bindApplication();
    }
    
    // Create shadow Intent
    Intent shadow = new Intent();
    shadow.setPackage(PrisonCore.getPackageName());
    shadow.setComponent(null);
    shadow.setAction(intent.getAction());
    return shadow;
}
```

**Flow**:
1. Query BroadcastReceivers
2. Start or bind target processes
3. Create and return a shadow Intent

#### Schedule BroadcastReceiver

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
    
    // Send to all receivers
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

### 4. ContentProvider Management

#### Acquire ContentProvider

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

**Flow**:
1. Start the provider's process
2. Get provider client via ActivityThread

### 5. Process Management

#### Initialize Process

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

**Purpose**:
- Start target process
- Return process configuration

#### Restart Process

```java
@Override
public void restartProcess(String packageName, String processName, int userId) {
    ProcessManagerService.get().restartAppProcess(packageName, processName, userId);
}
```

### 6. IntentSender Management

#### Register IntentSender

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

#### Query IntentSender

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

## Workflow

### 1. Request Arrives
- Receive calls via AIDL interface `IPActivityManagerService`
- Get caller PID and userId

### 2. Get User Space
```java
UserSpace userSpace = getOrCreateSpaceLocked(userId);
```
- Get or create user space by `userId`
- Ensure thread safety

### 3. Synchronized Execution
```java
synchronized (userSpace.mStack) {
    // operate on ActivityStack
}
```
- Execute operations synchronized on target component in the user space
- Protect critical operations with `synchronized`

### 4. Process Management
```java
ProcessRecord processRecord = ProcessManagerService.get()
    .startProcessLocked(packageName, processName, userId, ...);
```
- Start/manage processes via `ProcessManagerService` when needed
- Use `ProcessRecord` for subsequent operations

### 5. Lifecycle Callbacks
```java
processRecord.bActivityThread.scheduleReceiver(data);
```
- Communicate with client via `ProcessRecord.bActivityThread`
- Send lifecycle events and callbacks

### Full Flow Diagram

```
Client request
    ↓
AIDL interface receives
    ↓
Get caller info (PID, userId)
    ↓
getOrCreateSpaceLocked(userId)
    ↓
Get user space (UserSpace)
    ├─ mStack (ActivityStack)
    ├─ mActiveServices (ActiveServices)
    └─ mIntentSenderRecords
    ↓
Synchronized execution
    ↓
Need process?
    ├─ Yes → ProcessManagerService.startProcessLocked()
    └─ No → operate directly
    ↓
Communicate via ProcessRecord.bActivityThread
    ↓
Return result
```

---

## Key Features

### 1. Multi-User Isolation
- **Mechanism**: Each user has an independent `UserSpace`
- **Implementation**: `Map<Integer, UserSpace> mUserSpace`
- **Benefit**: Full isolation, no interference

### 2. Thread Safety
- **Mechanism**: Use `synchronized` to protect critical operations
- **Scope**: UserSpace access, component operations
- **Benefit**: Avoid concurrency issues

### 3. Process Lifecycle Management
- **Integration**: Tight integration with `ProcessManagerService`
- **Functions**: Start, restart, find processes
- **Benefit**: Unified process lifecycle management

### 4. Proxy Mechanism
- **Activity**: Virtualized via `ProxyActivity`
- **Service**: Virtualized via `ProxyService`
- **Benefit**: Run target components in the virtual environment

### 5. Unified Interface
- **AIDL**: Unified interface via `IPActivityManagerService`
- **Implementation**: All operations go through AIDL
- **Benefit**: Cross-process calls with standardized interface

---

## Class Relationship Diagram

```
ActivityManagerService
    │
    ├─ UserSpace (one per user)
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
    │   └─ resolve component info
    │
    ├─ ProcessManagerService
    │   └─ manage process lifecycle
    │
    └─ BroadcastManager
        └─ manage Broadcast delivery
```

---

## Initialization

### System Ready

```java
@Override
public void systemReady() {
    mBroadcastManager.startup();
}
```

**Purpose**:
- Called when the system is ready
- Starts BroadcastManager

### Constructor

```java
public ActivityManagerService() {
    mBroadcastManager = BroadcastManager.startSystem(this, mPms);
}
```

**Initialization**:
- Create BroadcastManager
- Pass references to itself and PackageManagerService

---

## Summary

As the core service of the virtualized environment, `ActivityManagerService` manages components via:

1. **User isolation**: each user has an independent UserSpace
2. **Component management**: unified management of Activity, Service, Broadcast, ContentProvider
3. **Process integration**: tight integration with ProcessManagerService
4. **Thread safety**: `synchronized` protects critical operations
5. **Proxy mechanism**: Proxy components provide virtualization

This design enables component management in a multi-user environment, ensuring each user's components
run in independent spaces without interference.
