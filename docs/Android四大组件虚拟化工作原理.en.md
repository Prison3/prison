# Virtualization of Android's Four Core Components

## Contents

1. [Overview](#overview)
2. [Core Design Principles](#core-design-principles)
3. [Activity Virtualization](#activity-virtualization)
4. [Service Virtualization](#service-virtualization)
5. [BroadcastReceiver Virtualization](#broadcastreceiver-virtualization)
6. [ContentProvider Virtualization](#contentprovider-virtualization)
7. [Proxy Component Management](#proxy-component-management)
8. [Multi-User Isolation](#multi-user-isolation)
9. [Workflow Summary](#workflow-summary)
10. [Key Technical Points](#key-technical-points)

---

## Overview

Virtualizing Android’s four core components (Activity, Service, BroadcastReceiver, ContentProvider)
is a core capability of Prison. Using proxy replacement, Prison provides isolated runtime
environments for apps without modifying their code, enabling multi-user isolation and unified
component management.

### Why Virtualize
- **Multi-user isolation**: Each app runs in an independent user space
- **Unified component management**: Centralized lifecycle management
- **Transparent proxying**: No app code changes required
- **Resource isolation**: Independent processes, storage, and permission spaces

---

## Core Design Principles

### 1. Two-Step Proxy Replacement

1. **Replace stage**: Replace the original component’s ComponentName with a proxy component
2. **Restore stage**: Restore the original component inside the proxy and run it

```
Original Component → Proxy Component → Restore Original Component
```

### 2. Metadata Preservation

Original component info is stored in Intent extras:
- Original Intent
- Component metadata (ActivityInfo/ServiceInfo)
- User ID
- Other required fields

### 3. Proxy Pool

Predefined proxy components (P0–P49) to avoid conflicts:
- Each process uses a different proxy
- Selected by process ID (vpid)
- Supports concurrent component launches

---

## Activity Virtualization

### Architecture

Activity virtualization uses `ProxyActivity` with 50 proxy Activities (P0–P49).

**Core classes**:
- `ProxyActivity` (P0–P49)
- `ProxyActivityRecord`
- `ActivityStack`

### Flow (high level)

```
Client call
    ↓
PActivityManager.startActivity()
    ↓
ActivityManagerService.startActivity()
    ↓
Get UserSpace
    ↓
ActivityStack.startActivityLocked()
    ↓
Resolve Intent → find/create TaskRecord
    ↓
Handle launch mode → create ActivityRecord
    ↓
Generate proxy Intent → replace ComponentName
    ↓
Save original info → ProxyActivityRecord.saveStub()
    ↓
Launch ProxyActivity
    ↓
ProxyActivity.onCreate() → restore original Activity
    ↓
onActivityCreated() → add to task stack
```

### Proxy replacement (key snippet)

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

### Key Features
- Launch modes: standard, singleTop, singleTask, singleInstance
- Task stack management: TaskRecord-based stacks per user
- Lifecycle via HCallbackProxy hooks

---

## Service Virtualization

### Architecture

Service virtualization uses `ProxyService` with 50 proxy Services (P0–P49).

**Core classes**:
- `ProxyService` (P0–P49)
- `ProxyServiceRecord`
- `AppServiceDispatcher`

### Flow (high level)

```
Client call
    ↓
PActivityManager.startService()
    ↓
ActivityManagerService.startService()
    ↓
ActiveServices.startService()
    ↓
Start process → build proxy Intent
    ↓
ProxyService.onStartCommand()
    ↓
Restore original Service → dispatch to target
```

### Key Features
- Supports start/bind/stop/unbind
- Service lifecycle bridged by AppServiceDispatcher
- Uses proxy Service pool (P0–P49)

---

## BroadcastReceiver Virtualization

### Flow (high level)

```
Client sendBroadcast()
    ↓
ActivityManagerService.sendBroadcast()
    ↓
Query receivers in virtual PM
    ↓
Bind target processes
    ↓
Schedule receiver via ActivityThread
    ↓
ProxyBroadcastReceiver → restore target receiver
```

### Key Features
- Isolated receiver resolution per user
- Supports ordered and normal broadcasts
- Uses ProxyBroadcastReceiver to restore original receiver

---

## ContentProvider Virtualization

### Flow (high level)

```
Client acquireProvider()
    ↓
ActivityManagerService.acquireContentProviderClient()
    ↓
Start provider process
    ↓
ActivityThread.acquireContentProviderClient()
    ↓
Return proxy provider binder
```

### Key Features
- Uses provider stubs and proxy IContentProvider
- Supports per-user provider isolation
- Intercepts and normalizes UID/AttributionSource

---

## Proxy Component Management

### Proxy Pool
- Activities: `ProxyActivity$P0`–`P49`
- Services: `ProxyService$P0`–`P49`
- Transparent Activities: `TransparentProxyActivity$P0`–`P49`
- Proxy selection via `ProxyManifest` and vpid

### Example

```java
String proxyActivity = ProxyManifest.getProxyActivity(vpid % 50);
Intent proxyIntent = new Intent(originalIntent);
proxyIntent.setComponent(new ComponentName(
    PrisonCore.getPackageName(), 
    proxyActivity
));
ProxyActivityRecord.saveStub(proxyIntent, originalIntent, 
                             activityInfo, activityRecord, userId);
```

---

## Multi-User Isolation

- Each user has an independent `UserSpace`
- Separate ActivityStack/ActiveServices/IntentSender records
- Package and component resolution is user-scoped

---

## Workflow Summary

1. Replace original component with a proxy component
2. Save original metadata in Intent extras
3. System launches proxy component
4. Proxy restores original metadata and dispatches to target
5. Component lifecycle is managed within the virtual user space

---

## Key Technical Points

- **Two-step replacement** enables transparent virtualization
- **Proxy pools** avoid component conflicts across processes
- **UserSpace isolation** guarantees multi-user boundaries
- **Lifecycle hooks** synchronize Activity/Service state
- **Fallback paths** allow system handling when virtualization can’t resolve
