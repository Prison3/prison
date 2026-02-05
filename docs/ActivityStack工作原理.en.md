# ActivityStack Internals

## Contents
1. [Overview](#overview)
2. [Core Architecture](#core-architecture)
3. [Data Structures](#data-structures)
4. [Activity Launch Flow](#activity-launch-flow)
5. [Activity Replacement Mechanism](#activity-replacement-mechanism)
6. [Lifecycle Management](#lifecycle-management)
7. [Launch Mode Handling](#launch-mode-handling)
8. [Task Stack Synchronization](#task-stack-synchronization)
9. [Key Method Reference](#key-method-reference)

---

## Overview

`ActivityStack` is the Activity stack manager in the virtualized environment. It manages Activity
launch, lifecycle, and task stacks. It implements Activity virtualization via proxy mechanisms in a
multi-user environment.

### Main Responsibilities
- Manage Activity task stacks (TaskRecord)
- Handle Activity launch modes and Intent flags
- Implement Activity proxy replacement
- Manage Activity lifecycle
- Synchronize system task stacks

---

## Core Architecture

### Class Structure

```java
public class ActivityStack {
    private final ActivityManager mAms;                    // system ActivityManager
    private final Map<Integer, TaskRecord> mTasks;        // task map (taskId -> TaskRecord)
    private final Set<ActivityRecord> mLaunchingActivities; // launching Activities
    private final Handler mHandler;                        // handle launch timeout
}
```

### Key Components

1. **TaskRecord**: Represents a task stack containing multiple Activities
2. **ActivityRecord**: Represents an Activity instance
3. **ProxyActivity**: Proxy Activity used to replace original Activity
4. **ProxyActivityRecord**: Proxy record storing original Activity info

---

## Data Structures

### TaskRecord

```java
public class TaskRecord {
    public int id;                              // task ID
    public int userId;                          // user ID
    public String taskAffinity;                 // task affinity
    public Intent rootIntent;                   // root Intent
    public final List<ActivityRecord> activities; // Activity list
}
```

**Role**:
- Manage a group of related Activities
- Identify tasks via `taskAffinity`
- Maintain Activity stack ordering

### ActivityRecord

```java
public class ActivityRecord extends Binder {
    public TaskRecord task;                     // owning task
    public IBinder token;                        // Activity token
    public IBinder resultTo;                     // result receiver
    public ActivityInfo info;                    // Activity info
    public ComponentName component;             // component name
    public Intent intent;                       // Intent
    public int userId;                          // user ID
    public boolean finished;                    // finished flag
    public ProcessRecord processRecord;         // process record
}
```

**Role**:
- Represent an Activity instance
- Link to TaskRecord and ProcessRecord
- Track Activity lifecycle state

---

## Activity Launch Flow

### 1. Entry Point

```java
public int startActivityLocked(int userId, Intent intent, String resolvedType, 
                               IBinder resultTo, String resultWho, int requestCode, 
                               int flags, Bundle options)
```

### 2. Steps

#### Step 1: Synchronize task stacks
```java
synchronized (mTasks) {
    synchronizeTasks();  // sync task state from system
}
```

#### Step 2: Resolve Activity
```java
ResolveInfo resolveInfo = PackageManagerService.get()
    .resolveActivity(intent, GET_ACTIVITIES, resolvedType, userId);
ActivityInfo activityInfo = resolveInfo.activityInfo;
```

#### Step 3: Find or create task
```java
String taskAffinity = ComponentUtils.getTaskAffinity(activityInfo);
TaskRecord taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity);
```

#### Step 4: Handle launch modes
- `LAUNCH_SINGLE_TOP`: reuse top
- `LAUNCH_SINGLE_TASK`: reuse within task
- `LAUNCH_SINGLE_INSTANCE`: single instance
- `LAUNCH_MULTIPLE`: multiple instances

#### Step 5: Handle Intent flags
- `FLAG_ACTIVITY_CLEAR_TOP`: clear activities above
- `FLAG_ACTIVITY_NEW_TASK`: new task
- `FLAG_ACTIVITY_CLEAR_TASK`: clear task

#### Step 6: Launch Activity
- New task: `startActivityInNewTaskLocked()`
- Existing task: `startActivityInSourceTask()`

### 3. Flow Diagram

```
Start Activity
    ↓
Synchronize tasks (synchronizeTasks)
    ↓
Resolve Intent (resolveActivity)
    ↓
Find/create TaskRecord
    ↓
Check launch mode
    ↓
Handle Intent flags
    ↓
Create ActivityRecord
    ↓
Start process (ProcessManagerService)
    ↓
Generate proxy Intent (getStartStubActivityIntentInner)
    ↓
Replace ComponentName → ProxyActivity
    ↓
Save original info (ProxyActivityRecord.saveStub)
    ↓
Launch ProxyActivity
    ↓
ProxyActivity.onCreate() restores original Activity
```

---

## Activity Replacement Mechanism

### Replacement Principle

ActivityStack virtualizes Activity via a **two-step replacement**:

1. **Step 1: ComponentName replacement**
   - Replace original Activity ComponentName with ProxyActivity
   - Save original Activity info into Intent extras

2. **Step 2: Restore original Activity**
   - ProxyActivity restores original Intent in onCreate()
   - Relaunches the original Activity

### Replacement Implementation

#### 1. Replacement entry: `getStartStubActivityIntentInner()`

**Path**: `ActivityStack.java:332-364`

```java
private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                               int userId, ProxyActivityRecord target,
                                               ActivityInfo activityInfo) {
    Intent shadow = new Intent();
    
    // Choose proxy Activity by theme
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
    
    // Save original info
    ProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, 
                                 target.mActivityRecord, target.mUserId);
    return shadow;
}
```

**Key operations**:
- **Lines 349-351**: Replace ComponentName
  - Translucent window → `TransparentProxyActivity$P{vpid}`
  - Normal window → `ProxyActivity$P{vpid}`
- **Line 362**: Save original info

#### 2. Save original info: `ProxyActivityRecord.saveStub()`

**Path**: `ProxyActivityRecord.java:22-27`

```java
public static void saveStub(Intent shadow, Intent target, ActivityInfo activityInfo, 
                           IBinder activityRecord, int userId) {
    shadow.putExtra("_B_|_user_id_", userId);
    shadow.putExtra("_B_|_activity_info_", activityInfo);
    shadow.putExtra("_B_|_target_", target);
    BundleCompat.putBinder(shadow, "_B_|_activity_record_v_", activityRecord);
}
```

**Saved fields**:
- `_B_|_user_id_`: user ID
- `_B_|_activity_info_`: original ActivityInfo
- `_B_|_target_`: original Intent (with original ComponentName)
- `_B_|_activity_record_v_`: ActivityRecord Binder

#### 3. Restore original Activity: `ProxyActivity.onCreate()`

**Path**: `ProxyActivity.java:17-32`

```java
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    finish();  // finish proxy immediately
    
    // Restore original Activity info
    ProxyActivityRecord record = ProxyActivityRecord.create(getIntent());
    if (record.mTarget != null) {
        record.mTarget.setExtrasClassLoader(
            PActivityThread.getApplication().getClassLoader()
        );
        // Launch original Activity
        startActivity(record.mTarget);
        return;
    }
}
```

**Key operations**:
- **Line 26**: Restore `ProxyActivityRecord` from Intent extras
- **Line 29**: Launch original Activity (`record.mTarget` contains original ComponentName)

### Proxy Activity Selection

**Path**: `ProxyManifest.java:26-32`

```java
public static String getProxyActivity(int index) {
    return String.format("com.android.prison.proxy.ProxyActivity$P%d", index);
}

public static String TransparentProxyActivity(int index) {
    return String.format("com.android.prison.proxy.TransparentProxyActivity$P%d", index);
}
```

**Mechanism**:
- Use process ID (`vpid`) as index
- Choose a proxy class from `P0` to `P49`
- Each process uses a different proxy class to avoid conflicts

### Replacement Flow

```
Original Intent: com.example.app.MainActivity
    ↓
[ActivityStack.getStartStubActivityIntentInner()]
    ↓
Replace ComponentName
    ├─ Translucent → TransparentProxyActivity$P{vpid}
    └─ Normal → ProxyActivity$P{vpid}
    ↓
Save original info into Intent extras
    ├─ _B_|_user_id_
    ├─ _B_|_activity_info_
    ├─ _B_|_target_ (original Intent)
    └─ _B_|_activity_record_v_
    ↓
System launches ProxyActivity$P{vpid}
    ↓
[ProxyActivity.onCreate()]
    ↓
Restore ProxyActivityRecord from extras
    ↓
Restore original Intent: com.example.app.MainActivity
    ↓
Relaunch original Activity
```

### Key Code Locations

| Step | File | Method | Line | Role |
|------|------|--------|------|------|
| 1. Create proxy record | `ActivityStack.java` | `startActivityProcess()` | 270 | Create `ProxyActivityRecord` |
| 2. Replace ComponentName | `ActivityStack.java` | `getStartStubActivityIntentInner()` | 349-351 | Replace with proxy Activity |
| 3. Save original info | `ProxyActivityRecord.java` | `saveStub()` | 22 | Save to Intent extras |
| 4. Restore Activity | `ProxyActivity.java` | `onCreate()` | 26-29 | Restore and launch original Activity |

### Why Replacement Is Needed

1. **Permission isolation**: Proxy Activity runs in the host process with required permissions
2. **Process management**: Use proxy to control target Activity process startup
3. **Multi-user support**: Use proxy for user isolation
4. **Virtualization**: Run target app inside the virtual environment

---

## Lifecycle Management

### 1. Activity Created: `onActivityCreated()`

**Path**: `ActivityStack.java:450-470`

```java
public void onActivityCreated(ProcessRecord processRecord, int taskId, 
                              IBinder token, ActivityRecord record) {
    // Remove from launching set
    synchronized (mLaunchingActivities) {
        mLaunchingActivities.remove(record);
        mHandler.removeMessages(LAUNCH_TIME_OUT, record);
    }
    
    // Create or get TaskRecord
    synchronized (mTasks) {
        synchronizeTasks();
        TaskRecord taskRecord = mTasks.get(taskId);
        if (taskRecord == null) {
            taskRecord = new TaskRecord(taskId, record.userId, 
                                       ComponentUtils.getTaskAffinity(record.info));
            taskRecord.rootIntent = record.intent;
            mTasks.put(taskId, taskRecord);
        }
        
        // Link ProcessRecord and TaskRecord
        record.token = token;
        record.processRecord = processRecord;
        record.task = taskRecord;
        taskRecord.addTopActivity(record);
    }
}
```

**Actions**:
- Remove launch timeout messages
- Create or get TaskRecord
- Link ProcessRecord
- Add to top of task stack

### 2. Activity Resumed: `onActivityResumed()`

**Path**: `ActivityStack.java:472-483`

```java
public void onActivityResumed(int userId, IBinder token) {
    synchronized (mTasks) {
        synchronizeTasks();
        ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
        if (activityRecord == null) {
            return;
        }
        // Move to top
        activityRecord.task.removeActivity(activityRecord);
        activityRecord.task.addTopActivity(activityRecord);
    }
}
```

**Actions**:
- Find ActivityRecord
- Remove from task stack
- Re-add to top

### 3. Activity Destroyed: `onActivityDestroyed()`

**Path**: `ActivityStack.java:485-496`

```java
public void onActivityDestroyed(int userId, IBinder token) {
    synchronized (mTasks) {
        synchronizeTasks();
        ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
        if (activityRecord == null) {
            return;
        }
        activityRecord.finished = true;
        activityRecord.task.removeActivity(activityRecord);
    }
}
```

**Actions**:
- Mark finished
- Remove from task stack

### 4. Activity Finished: `onFinishActivity()`

**Path**: `ActivityStack.java:498-508`

```java
public void onFinishActivity(int userId, IBinder token) {
    synchronized (mTasks) {
        synchronizeTasks();
        ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
        if (activityRecord == null) {
            return;
        }
        activityRecord.finished = true;
    }
}
```

**Actions**:
- Mark finished (not removed immediately)

---

## Launch Mode Handling

### 1. LAUNCH_SINGLE_TOP

**Logic** (`ActivityStack.java:186-199`):
```java
if (singleTop && !clearTop) {
    if (ComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
        newIntentRecord = topActivityRecord;  // reuse top Activity
    } else {
        // check if launching
        for (ActivityRecord launchingActivity : mLaunchingActivities) {
            if (launchingActivity.component.equals(intent.getComponent())) {
                ignore = true;  // ignore duplicate launch
            }
        }
    }
}
```

**Behavior**:
- If top is target Activity, call `onNewIntent()`
- Otherwise create a new instance

### 2. LAUNCH_SINGLE_TASK

**Logic** (`ActivityStack.java:201-222`):
```java
if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK && !clearTop) {
    if (ComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
        newIntentRecord = topActivityRecord;  // reuse top
    } else {
        ActivityRecord record = findActivityRecordByComponentName(...);
        if (record != null) {
            newIntentRecord = record;
            // clear activities above target
            for (int i = taskRecord.activities.size() - 1; i >= 0; i--) {
                ActivityRecord next = taskRecord.activities.get(i);
                if (next != record) {
                    next.finished = true;
                } else {
                    break;
                }
            }
        }
    }
}
```

**Behavior**:
- Find target Activity in task stack
- Clear activities above it
- Call `onNewIntent()`

### 3. LAUNCH_SINGLE_INSTANCE

**Logic** (`ActivityStack.java:224-226`):
```java
if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
    newIntentRecord = topActivityRecord;  // reuse top
}
```

**Behavior**:
- Reuse top Activity
- Call `onNewIntent()`

### 4. FLAG_ACTIVITY_CLEAR_TOP

**Logic** (`ActivityStack.java:163-184`):
```java
if (clearTop) {
    if (targetActivityRecord != null) {
        // clear activities above target
        for (int i = targetActivityRecord.task.activities.size() - 1; i >= 0; i--) {
            ActivityRecord next = targetActivityRecord.task.activities.get(i);
            if (next != targetActivityRecord) {
                next.finished = true;
            } else {
                if (singleTop) {
                    newIntentRecord = targetActivityRecord;  // reuse
                } else {
                    targetActivityRecord.finished = true;  // recreate
                }
                break;
            }
        }
    }
}
```

**Behavior**:
- Clear activities above target
- If `singleTop`, reuse target Activity
- Otherwise recreate target Activity

---

## Task Stack Synchronization

### Synchronization: `synchronizeTasks()`

**Path**: `ActivityStack.java:538-551`

```java
@SuppressWarnings("deprecation")
private void synchronizeTasks() {
    // Get recent tasks from system
    List<ActivityManager.RecentTaskInfo> recentTasks = mAms.getRecentTasks(100, 0);
    Map<Integer, TaskRecord> newTacks = new LinkedHashMap<>();
    
    // Keep only tasks that still exist in system
    for (int i = recentTasks.size() - 1; i >= 0; i--) {
        ActivityManager.RecentTaskInfo next = recentTasks.get(i);
        TaskRecord taskRecord = mTasks.get(next.id);
        if (taskRecord == null)
            continue;
        newTacks.put(next.id, taskRecord);
    }
    
    // Update task stack
    mTasks.clear();
    mTasks.putAll(newTacks);
}
```

**Purpose**:
- Fetch recent tasks from system ActivityManager
- Synchronize internal task stacks
- Remove tasks that no longer exist in the system

**When called**:
- Before starting Activities
- During Activity lifecycle callbacks
- When querying tasks

---

## Key Method Reference

### Launch-related Methods

#### `startActivityLocked()`
- **Role**: Main entry for Activity launch
- **Args**: userId, intent, resolvedType, resultTo, resultWho, requestCode, flags, options
- **Return**: result code

#### `startActivityInNewTaskLocked()`
- **Role**: Start Activity in a new task
- **Flow**: Create ActivityRecord → start process → generate proxy Intent → launch

#### `startActivityInSourceTask()`
- **Role**: Start Activity in an existing task
- **Flow**: Create ActivityRecord → start process → generate proxy Intent → start via AMS

#### `startActivityProcess()`
- **Role**: Start target process and generate proxy Intent
- **Return**: proxy Intent

#### `getStartStubActivityIntentInner()`
- **Role**: Generate proxy Activity Intent
- **Key**: Replace ComponentName and save original info

### Lookup Methods

#### `findActivityRecordByToken()`
- **Role**: Find ActivityRecord by token
- **Usage**: Locate Activity during lifecycle callbacks

#### `findActivityRecordByComponentName()`
- **Role**: Find ActivityRecord by ComponentName
- **Usage**: Find existing Activity during launch mode handling

#### `findTaskRecordByTaskAffinityLocked()`
- **Role**: Find TaskRecord by taskAffinity
- **Usage**: Find existing task stack during launch

#### `findTaskRecordByTokenLocked()`
- **Role**: Find TaskRecord by token
- **Usage**: Locate task stack by Activity token

### Lifecycle Methods

#### `onActivityCreated()`
- **Role**: Activity created callback
- **Actions**: Create/get TaskRecord, link ProcessRecord

#### `onActivityResumed()`
- **Role**: Activity resumed callback
- **Actions**: Move Activity to top

#### `onActivityDestroyed()`
- **Role**: Activity destroyed callback
- **Actions**: Mark finished, remove from stack

#### `onFinishActivity()`
- **Role**: Activity finished callback
- **Actions**: Mark finished

### Helper Methods

#### `deliverNewIntentLocked()`
- **Role**: Deliver new Intent to Activity
- **Called**: When reusing Activity via launch mode

#### `finishAllActivity()`
- **Role**: Finish all Activities marked finished
- **Called**: On CLEAR_TASK

#### `newActivityRecord()`
- **Role**: Create new ActivityRecord
- **Actions**: Add to launching set, set timeout

#### `synchronizeTasks()`
- **Role**: Sync system task stacks
- **Called**: Before launch, during lifecycle callbacks

---

## Summary

`ActivityStack` manages Activities through:

1. **Task management**: TaskRecord for tasks, ActivityRecord for Activities
2. **Proxy replacement**: ProxyActivity provides virtualization
3. **Launch modes**: standard, singleTop, singleTask, singleInstance
4. **Intent flags**: CLEAR_TOP, NEW_TASK, CLEAR_TASK, etc.
5. **Lifecycle**: create, resume, destroy
6. **Task sync**: synchronize with system ActivityManager

This design enables Activity virtualization in a multi-user environment, ensuring each user's
Activities run in independent processes and task stacks.
