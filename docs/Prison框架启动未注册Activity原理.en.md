# How Prison Launches Unregistered Activities

## Overview

Android requires Activities to be declared in `AndroidManifest.xml`. Prison bypasses this
restriction by combining **proxy replacement** and **message loop interception**, allowing
Activities not declared in the manifest to be launched.

---

## Core Idea

Prison uses a **two-step replacement**:

1. **Replace phase**: Replace the original Activity with a registered `ProxyActivity`
2. **Restore phase**: Intercept the message loop and restore the original Activity info

---

## End-to-End Flow

### 1. Activity Launch Request

```
App calls startActivity(originalIntent)
    ↓
ActivityManagerCommonProxy.StartActivity.hook()
    ↓
Virtual PackageManager.resolveActivity()
    ↓
ActivityManagerService.startActivityAms()
    ↓
ActivityStack.startActivityLocked()
```

### 2. Proxy Replacement (ActivityStack)

`ActivityStack.startActivityLocked()` calls `getStartStubActivityIntentInner()` to create a proxy
Intent:

```java
private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                               int userId, ProxyActivityRecord target,
                                               ActivityInfo activityInfo) {
    Intent shadow = new Intent();
    boolean windowIsTranslucent = ...;
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
    ProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, 
                                 target.mActivityRecord, target.mUserId);
    return shadow;
}
```

**Key points**:
- Replace ComponentName with a registered ProxyActivity
- Save original Intent/ActivityInfo in extras

### 3. Save Original Info (ProxyActivityRecord)

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

### 4. Launch ProxyActivity

```java
Intent shadow = startActivityProcess(userId, intent, activityInfo, record);
shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
PrisonCore.getContext().startActivity(shadow);
```

At this stage the system is launching **ProxyActivity** (registered), not the original Activity.

### 5. Message Loop Interception (HCallbackProxy)

`HCallbackProxy` hooks `ActivityThread.mH`:

```java
@Override
public boolean handleMessage(@NonNull Message msg) {
    if (BuildCompat.isPie()) {
        if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION()) {
            if (handleLaunchActivity(msg.obj)) {
                getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                return true;
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
    if (mOtherCallback != null) {
        return mOtherCallback.handleMessage(msg);
    }
    return false;
}
```

### 6. Restore Original Activity (handleLaunchActivity)

High-level logic:

1. Extract proxy Intent from ClientTransaction/ActivityClientRecord
2. Restore original info from extras (`ProxyActivityRecord`)
3. If process not initialized, restart/bind and update transaction data
4. Replace Intent/ActivityInfo in transaction record
5. Notify `ActivityManagerService` about Activity creation

After this, the system continues and instantiates the **original Activity**, not the proxy.

### 7. Activity Creation

```
ActivityThread.performLaunchActivity()
    ↓
Instantiate original Activity (via reflection)
    ↓
Call Activity.onCreate()
```

---

## Key Code Locations

- `ActivityStack.getStartStubActivityIntentInner()` — build proxy Intent
- `ProxyActivityRecord.saveStub()` — save original metadata
- `HCallbackProxy.handleMessage()` — intercept launch message
- `HCallbackProxy.handleLaunchActivity()` — restore original info
- `ProxyActivity.onCreate()` — final restoration path for proxy-only cases

---

## Summary

Prison launches unregistered Activities by:

1. Replacing the original Activity with a registered proxy
2. Storing original metadata inside the proxy Intent
3. Intercepting the launch message and restoring the original Activity
4. Letting the system continue to instantiate the original class

This approach preserves Android’s manifest requirements while enabling virtualization and
multi-user isolation.
