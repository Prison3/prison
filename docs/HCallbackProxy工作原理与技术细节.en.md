# HCallbackProxy Internals and Technical Details

## Contents

1. [Overview](#overview)
2. [Core Mechanism](#core-mechanism)
3. [Injection Mechanism](#injection-mechanism)
4. [Message Interception](#message-interception)
5. [Activity Launch Handling](#activity-launch-handling)
6. [Service Creation Handling](#service-creation-handling)
7. [Multi-Version Compatibility](#multi-version-compatibility)
8. [Thread Safety](#thread-safety)
9. [Technical Notes](#technical-notes)
10. [Key Code Analysis](#key-code-analysis)

---

## Overview

`HCallbackProxy` is one of the most critical hook components in Prison. It hooks the
`ActivityThread.mH` `Callback` and intercepts Activity/Service creation messages in the main
message loop, enabling component virtualization.

### Core Responsibilities

1. **Intercept Activity launch messages**: swap proxy Activity back to original before the system
   creates it
2. **Intercept Service creation messages**: replace proxy Service with the original Service
3. **Restore original metadata**: recover from proxy Intent extras
4. **Process management**: ensure process init/bind during early launch

### Why HCallbackProxy?

Android starts Activities via `ActivityThread.mH` messages. Prison must intercept these messages
before they are handled and restore the original Activity info so the system instantiates the
correct Activity class.

---

## Core Mechanism

### Handler.Callback Hook

`HCallbackProxy` implements `Handler.Callback` and replaces `ActivityThread.mH.mCallback`:

```java
public class HCallbackProxy implements IInjector, Handler.Callback {
    private Handler.Callback mOtherCallback;  // original Callback
    private AtomicBoolean mBeing = new AtomicBoolean(false);  // re-entrance guard
    
    @Override
    public boolean handleMessage(@NonNull Message msg) {
        // intercept and handle
    }
}
```

### ActivityThread.mH Behavior

```java
public final class ActivityThread {
    final Handler mH = new Handler() {
        public void handleMessage(Message msg) {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            // handle message...
        }
    };
}
```

**Key point**:
- If `mCallback.handleMessage()` returns `true`, the message is consumed and default handling stops.

---

## Injection Mechanism

### inject()

```java
@Override
public void inject() {
    mOtherCallback = getHCallback();
    if (mOtherCallback != null && 
        (mOtherCallback == this || 
         mOtherCallback.getClass().getName().equals(this.getClass().getName()))) {
        mOtherCallback = null;
    }
    BRHandler.get(getH())._set_mCallback(this);
}
```

### Accessing Handler/Callback

```java
private Handler getH() {
    Object currentActivityThread = PrisonCore.mainThread();
    return BRActivityThread.get(currentActivityThread).mH();
}

private Handler.Callback getHCallback() {
    return BRHandler.get(getH()).mCallback();
}
```

### Environment Check

```java
@Override
public boolean isBadEnv() {
    Handler.Callback hCallback = getHCallback();
    return hCallback != null && hCallback != this;
}
```

If another framework has replaced the callback, re-inject.

---

## Message Interception

### handleMessage() Core Logic

```java
@Override
public boolean handleMessage(@NonNull Message msg) {
    if (!mBeing.getAndSet(true)) {
        try {
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
            if (msg.what == BRActivityThreadH.get().CREATE_SERVICE()) {
                return handleCreateService(msg.obj);
            }
            if (mOtherCallback != null) {
                return mOtherCallback.handleMessage(msg);
            }
            return false;
        } finally {
            mBeing.set(false);
        }
    }
    return false;
}
```

### Key Message Types

| Message | Android Version | Notes |
|---------|------------------|------|
| `EXECUTE_TRANSACTION` | 9.0+ | ClientTransaction |
| `LAUNCH_ACTIVITY` | 8.0 and below | ActivityClientRecord |
| `CREATE_SERVICE` | All | Service creation |

---

## Activity Launch Handling

### handleLaunchActivity() High-Level Flow

```java
private boolean handleLaunchActivity(Object client) {
    Object r = BuildCompat.isPie() ? getLaunchActivityItem(client) : client;
    Intent intent;
    IBinder token;
    if (BuildCompat.isPie()) {
        intent = BRLaunchActivityItem.get(r).mIntent();
        token = BRClientTransaction.get(client).mActivityToken();
    } else {
        ActivityThreadActivityClientRecordContext clientRecordContext = 
            BRActivityThreadActivityClientRecord.get(r);
        intent = clientRecordContext.intent();
        token = clientRecordContext.token();
    }
    ProxyActivityRecord stubRecord = ProxyActivityRecord.create(intent);
    ActivityInfo activityInfo = stubRecord.mActivityInfo;
    if (activityInfo != null) {
        // restore info, ensure process state, update records
    }
    return false;
}
```

### Process Not Initialized

If `PActivityThread.getAppConfig()` is null:
- Restart process
- Build launch Intent
- Re-save stub info into Intent
- Update ClientTransaction/ActivityClientRecord

### Process Not Bound

If process is not bound:
```java
PActivityThread.currentActivityThread().bindApplication(
    activityInfo.packageName, activityInfo.processName
);
```

### Restore Original Activity Info

Depending on Android version (Tiramisu/S/Pie/Legacy), update:
- Intent to original (`stubRecord.mTarget`)
- ActivityInfo to original
- PackageInfo for Android 12 special path
- Notify AMS via `PActivityManager.get().onActivityCreated(...)`

---

## Service Creation Handling

The hook skips proxy services and restores actual ServiceInfo for the target app, then updates
`CreateServiceData` to ensure the system creates the correct Service class in the virtualized
context.

---

## Multi-Version Compatibility

Prison handles:
- **Android 9+** via ClientTransaction / LaunchActivityItem
- **Android 8 and below** via ActivityClientRecord
- **Android 12/13** with special package info and client record updates

---

## Thread Safety

`AtomicBoolean mBeing` prevents re-entrant handling when a message triggers additional messages:

```java
private AtomicBoolean mBeing = new AtomicBoolean(false);
if (!mBeing.getAndSet(true)) {
    try {
        // handle
    } finally {
        mBeing.set(false);
    }
}
```

---

## Technical Notes

- Uses reflection helpers (`BRActivityThread`, `BRHandler`, `BRLaunchActivityItem`, etc.) to access
  hidden fields.
- Restores original component info from proxy Intent extras.
- Ensures process and binding state before handing control back to the system.

---

## Key Code Analysis

### getLaunchActivityItem()

Extracts `LaunchActivityItem` from ClientTransaction callbacks:

```java
private Object getLaunchActivityItem(Object clientTransaction) {
    List<Object> mActivityCallbacks = 
        BRClientTransaction.get(clientTransaction).mActivityCallbacks();
    if (mActivityCallbacks == null) {
        Logger.e(TAG, "mActivityCallbacks is null");
        return null;
    }
    for (Object obj : mActivityCallbacks) {
        if (BRLaunchActivityItem.getRealClass().getName()
            .equals(obj.getClass().getCanonicalName())) {
            return obj;
        }
    }
    return null;
}
```

This allows consistent handling of Activity launch info across Android versions.
