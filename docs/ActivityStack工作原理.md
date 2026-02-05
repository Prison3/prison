# ActivityStack 工作原理文档

## 目录
1. [概述](#概述)
2. [核心架构](#核心架构)
3. [数据结构](#数据结构)
4. [Activity 启动流程](#activity-启动流程)
5. [Activity 替换机制](#activity-替换机制)
6. [生命周期管理](#生命周期管理)
7. [启动模式处理](#启动模式处理)
8. [任务栈同步](#任务栈同步)
9. [关键方法说明](#关键方法说明)

---

## 概述

`ActivityStack` 是虚拟化环境中的 Activity 栈管理器，负责管理 Activity 的启动、生命周期和任务栈。它通过代理机制实现多用户环境下的 Activity 虚拟化。

### 主要职责
- 管理 Activity 任务栈（TaskRecord）
- 处理 Activity 启动模式和 Intent Flags
- 实现 Activity 代理替换机制
- 管理 Activity 生命周期
- 同步系统任务栈状态

---

## 核心架构

### 类结构

```java
public class ActivityStack {
    private final ActivityManager mAms;                    // 系统 ActivityManager
    private final Map<Integer, TaskRecord> mTasks;        // 任务栈映射 (taskId -> TaskRecord)
    private final Set<ActivityRecord> mLaunchingActivities; // 正在启动的 Activity 集合
    private final Handler mHandler;                        // 处理启动超时
}
```

### 关键组件

1. **TaskRecord**: 表示一个任务栈，包含多个 Activity
2. **ActivityRecord**: 表示一个 Activity 实例
3. **ProxyActivity**: 代理 Activity，用于替换原始 Activity
4. **ProxyActivityRecord**: 代理记录，保存原始 Activity 信息

---

## 数据结构

### TaskRecord（任务记录）

```java
public class TaskRecord {
    public int id;                              // 任务 ID
    public int userId;                          // 用户 ID
    public String taskAffinity;                 // 任务亲和性
    public Intent rootIntent;                   // 根 Intent
    public final List<ActivityRecord> activities; // Activity 列表
}
```

**作用**：
- 管理一组相关的 Activity
- 通过 `taskAffinity` 标识任务
- 维护 Activity 的栈结构

### ActivityRecord（Activity 记录）

```java
public class ActivityRecord extends Binder {
    public TaskRecord task;                     // 所属任务
    public IBinder token;                        // Activity Token
    public IBinder resultTo;                     // 结果接收者
    public ActivityInfo info;                    // Activity 信息
    public ComponentName component;             // 组件名称
    public Intent intent;                       // Intent
    public int userId;                          // 用户 ID
    public boolean finished;                    // 是否已完成
    public ProcessRecord processRecord;         // 进程记录
}
```

**作用**：
- 表示一个 Activity 实例
- 关联所属的 TaskRecord 和 ProcessRecord
- 记录 Activity 的生命周期状态

---

## Activity 启动流程

### 1. 启动入口

```java
public int startActivityLocked(int userId, Intent intent, String resolvedType, 
                               IBinder resultTo, String resultWho, int requestCode, 
                               int flags, Bundle options)
```

### 2. 启动步骤

#### 步骤 1: 同步任务栈
```java
synchronized (mTasks) {
    synchronizeTasks();  // 从系统同步任务栈状态
}
```

#### 步骤 2: 解析 Activity
```java
ResolveInfo resolveInfo = PackageManagerService.get()
    .resolveActivity(intent, GET_ACTIVITIES, resolvedType, userId);
ActivityInfo activityInfo = resolveInfo.activityInfo;
```

#### 步骤 3: 查找或创建任务栈
```java
String taskAffinity = ComponentUtils.getTaskAffinity(activityInfo);
TaskRecord taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity);
```

#### 步骤 4: 处理启动模式
- `LAUNCH_SINGLE_TOP`: 栈顶复用
- `LAUNCH_SINGLE_TASK`: 任务内复用
- `LAUNCH_SINGLE_INSTANCE`: 单例模式
- `LAUNCH_MULTIPLE`: 多实例

#### 步骤 5: 处理 Intent Flags
- `FLAG_ACTIVITY_CLEAR_TOP`: 清除顶部 Activity
- `FLAG_ACTIVITY_NEW_TASK`: 新任务
- `FLAG_ACTIVITY_CLEAR_TASK`: 清除任务

#### 步骤 6: 启动 Activity
- 新任务：`startActivityInNewTaskLocked()`
- 现有任务：`startActivityInSourceTask()`

### 3. 启动流程图

```
启动 Activity
    ↓
同步任务栈 (synchronizeTasks)
    ↓
解析 Intent (resolveActivity)
    ↓
查找/创建 TaskRecord
    ↓
检查启动模式 (launchMode)
    ↓
处理 Intent Flags
    ↓
创建 ActivityRecord
    ↓
启动进程 (ProcessManagerService)
    ↓
生成代理 Intent (getStartStubActivityIntentInner)
    ↓
替换 ComponentName → ProxyActivity
    ↓
保存原始信息 (ProxyActivityRecord.saveStub)
    ↓
启动 ProxyActivity
    ↓
ProxyActivity.onCreate() 恢复原始 Activity
```

---

## Activity 替换机制

### 替换原理

ActivityStack 通过**两步替换**机制实现 Activity 虚拟化：

1. **第一步：ComponentName 替换**
   - 将原始 Activity 的 ComponentName 替换为 ProxyActivity
   - 保存原始 Activity 信息到 Intent Extra

2. **第二步：恢复原始 Activity**
   - ProxyActivity 在 onCreate() 中恢复原始 Intent
   - 重新启动原始 Activity

### 替换实现位置

#### 1. 替换入口：`getStartStubActivityIntentInner()`

**位置**: `ActivityStack.java:332-364`

```java
private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                               int userId, ProxyActivityRecord target,
                                               ActivityInfo activityInfo) {
    Intent shadow = new Intent();
    
    // 根据主题选择代理 Activity
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
    
    // 保存原始信息
    ProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, 
                                 target.mActivityRecord, target.mUserId);
    return shadow;
}
```

**关键操作**：
- **第 349-351 行**: 替换 ComponentName
  - 透明窗口 → `TransparentProxyActivity$P{vpid}`
  - 普通窗口 → `ProxyActivity$P{vpid}`
- **第 362 行**: 保存原始信息

#### 2. 保存原始信息：`ProxyActivityRecord.saveStub()`

**位置**: `ProxyActivityRecord.java:22-27`

```java
public static void saveStub(Intent shadow, Intent target, ActivityInfo activityInfo, 
                           IBinder activityRecord, int userId) {
    shadow.putExtra("_B_|_user_id_", userId);
    shadow.putExtra("_B_|_activity_info_", activityInfo);
    shadow.putExtra("_B_|_target_", target);
    BundleCompat.putBinder(shadow, "_B_|_activity_record_v_", activityRecord);
}
```

**保存的信息**：
- `_B_|_user_id_`: 用户 ID
- `_B_|_activity_info_`: 原始 ActivityInfo
- `_B_|_target_`: 原始 Intent（包含原始 ComponentName）
- `_B_|_activity_record_v_`: ActivityRecord Binder

#### 3. 恢复原始 Activity：`ProxyActivity.onCreate()`

**位置**: `ProxyActivity.java:17-32`

```java
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    finish();  // 立即 finish 代理 Activity
    
    // 恢复原始 Activity 信息
    ProxyActivityRecord record = ProxyActivityRecord.create(getIntent());
    if (record.mTarget != null) {
        record.mTarget.setExtrasClassLoader(
            PActivityThread.getApplication().getClassLoader()
        );
        // 启动原始 Activity
        startActivity(record.mTarget);
        return;
    }
}
```

**关键操作**：
- **第 26 行**: 从 Intent Extra 恢复 `ProxyActivityRecord`
- **第 29 行**: 启动原始 Activity（`record.mTarget` 包含原始 ComponentName）

### 代理 Activity 选择机制

**位置**: `ProxyManifest.java:26-32`

```java
public static String getProxyActivity(int index) {
    return String.format("com.android.prison.proxy.ProxyActivity$P%d", index);
}

public static String TransparentProxyActivity(int index) {
    return String.format("com.android.prison.proxy.TransparentProxyActivity$P%d", index);
}
```

**机制**：
- 使用进程 ID（`vpid`）作为索引
- 选择 `P0` 到 `P49` 中的一个代理类
- 每个进程使用不同的代理类，避免冲突

### 替换流程图

```
原始 Intent: com.example.app.MainActivity
    ↓
[ActivityStack.getStartStubActivityIntentInner()]
    ↓
替换 ComponentName
    ├─ 透明窗口 → TransparentProxyActivity$P{vpid}
    └─ 普通窗口 → ProxyActivity$P{vpid}
    ↓
保存原始信息到 Intent Extra
    ├─ _B_|_user_id_
    ├─ _B_|_activity_info_
    ├─ _B_|_target_ (原始 Intent)
    └─ _B_|_activity_record_v_
    ↓
系统启动 ProxyActivity$P{vpid}
    ↓
[ProxyActivity.onCreate()]
    ↓
从 Extra 恢复 ProxyActivityRecord
    ↓
恢复原始 Intent: com.example.app.MainActivity
    ↓
重新启动原始 Activity
```

### 替换的关键代码位置

| 步骤 | 文件 | 方法 | 行号 | 作用 |
|------|------|------|------|------|
| 1. 创建代理记录 | `ActivityStack.java` | `startActivityProcess()` | 270 | 创建 `ProxyActivityRecord` |
| 2. 替换 ComponentName | `ActivityStack.java` | `getStartStubActivityIntentInner()` | 349-351 | 替换为代理 Activity |
| 3. 保存原始信息 | `ProxyActivityRecord.java` | `saveStub()` | 22 | 保存到 Intent Extra |
| 4. 恢复原始 Activity | `ProxyActivity.java` | `onCreate()` | 26-29 | 恢复并启动原始 Activity |

### 为什么需要替换？

1. **权限隔离**: 代理 Activity 在宿主进程中运行，拥有必要权限
2. **进程管理**: 通过代理控制目标 Activity 的进程启动
3. **多用户支持**: 通过代理实现用户隔离
4. **虚拟化**: 在虚拟环境中运行目标应用

---

## 生命周期管理

### 1. Activity 创建：`onActivityCreated()`

**位置**: `ActivityStack.java:450-470`

```java
public void onActivityCreated(ProcessRecord processRecord, int taskId, 
                              IBinder token, ActivityRecord record) {
    // 从启动集合移除
    synchronized (mLaunchingActivities) {
        mLaunchingActivities.remove(record);
        mHandler.removeMessages(LAUNCH_TIME_OUT, record);
    }
    
    // 创建或获取 TaskRecord
    synchronized (mTasks) {
        synchronizeTasks();
        TaskRecord taskRecord = mTasks.get(taskId);
        if (taskRecord == null) {
            taskRecord = new TaskRecord(taskId, record.userId, 
                                       ComponentUtils.getTaskAffinity(record.info));
            taskRecord.rootIntent = record.intent;
            mTasks.put(taskId, taskRecord);
        }
        
        // 关联 ProcessRecord 和 TaskRecord
        record.token = token;
        record.processRecord = processRecord;
        record.task = taskRecord;
        taskRecord.addTopActivity(record);
    }
}
```

**操作**：
- 移除启动超时消息
- 创建或获取 TaskRecord
- 关联 ProcessRecord
- 添加到任务栈顶部

### 2. Activity 恢复：`onActivityResumed()`

**位置**: `ActivityStack.java:472-483`

```java
public void onActivityResumed(int userId, IBinder token) {
    synchronized (mTasks) {
        synchronizeTasks();
        ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
        if (activityRecord == null) {
            return;
        }
        // 将 Activity 移到栈顶
        activityRecord.task.removeActivity(activityRecord);
        activityRecord.task.addTopActivity(activityRecord);
    }
}
```

**操作**：
- 查找 ActivityRecord
- 从任务栈移除
- 重新添加到栈顶

### 3. Activity 销毁：`onActivityDestroyed()`

**位置**: `ActivityStack.java:485-496`

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

**操作**：
- 标记为 finished
- 从任务栈移除

### 4. Activity 完成：`onFinishActivity()`

**位置**: `ActivityStack.java:498-508`

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

**操作**：
- 标记为 finished（不立即移除）

---

## 启动模式处理

### 1. LAUNCH_SINGLE_TOP

**处理逻辑** (`ActivityStack.java:186-199`):
```java
if (singleTop && !clearTop) {
    if (ComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
        newIntentRecord = topActivityRecord;  // 复用栈顶 Activity
    } else {
        // 检查是否正在启动
        for (ActivityRecord launchingActivity : mLaunchingActivities) {
            if (launchingActivity.component.equals(intent.getComponent())) {
                ignore = true;  // 忽略重复启动
            }
        }
    }
}
```

**行为**：
- 如果栈顶是目标 Activity，调用 `onNewIntent()`
- 否则创建新实例

### 2. LAUNCH_SINGLE_TASK

**处理逻辑** (`ActivityStack.java:201-222`):
```java
if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK && !clearTop) {
    if (ComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
        newIntentRecord = topActivityRecord;  // 复用栈顶
    } else {
        ActivityRecord record = findActivityRecordByComponentName(...);
        if (record != null) {
            newIntentRecord = record;
            // 清除目标 Activity 上方的所有 Activity
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

**行为**：
- 在任务栈中查找目标 Activity
- 如果存在，清除其上方的所有 Activity
- 调用 `onNewIntent()`

### 3. LAUNCH_SINGLE_INSTANCE

**处理逻辑** (`ActivityStack.java:224-226`):
```java
if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
    newIntentRecord = topActivityRecord;  // 复用栈顶
}
```

**行为**：
- 复用栈顶 Activity
- 调用 `onNewIntent()`

### 4. FLAG_ACTIVITY_CLEAR_TOP

**处理逻辑** (`ActivityStack.java:163-184`):
```java
if (clearTop) {
    if (targetActivityRecord != null) {
        // 清除目标 Activity 上方的所有 Activity
        for (int i = targetActivityRecord.task.activities.size() - 1; i >= 0; i--) {
            ActivityRecord next = targetActivityRecord.task.activities.get(i);
            if (next != targetActivityRecord) {
                next.finished = true;
            } else {
                if (singleTop) {
                    newIntentRecord = targetActivityRecord;  // 复用
                } else {
                    targetActivityRecord.finished = true;  // 重建
                }
                break;
            }
        }
    }
}
```

**行为**：
- 清除目标 Activity 上方的所有 Activity
- 如果 `singleTop`，复用目标 Activity
- 否则重建目标 Activity

---

## 任务栈同步

### 同步机制：`synchronizeTasks()`

**位置**: `ActivityStack.java:538-551`

```java
@SuppressWarnings("deprecation")
private void synchronizeTasks() {
    // 从系统获取最近任务
    List<ActivityManager.RecentTaskInfo> recentTasks = mAms.getRecentTasks(100, 0);
    Map<Integer, TaskRecord> newTacks = new LinkedHashMap<>();
    
    // 只保留系统中存在的任务
    for (int i = recentTasks.size() - 1; i >= 0; i--) {
        ActivityManager.RecentTaskInfo next = recentTasks.get(i);
        TaskRecord taskRecord = mTasks.get(next.id);
        if (taskRecord == null)
            continue;
        newTacks.put(next.id, taskRecord);
    }
    
    // 更新任务栈
    mTasks.clear();
    mTasks.putAll(newTacks);
}
```

**作用**：
- 从系统 ActivityManager 获取最近任务列表
- 同步内部任务栈状态
- 移除系统中已不存在的任务

**调用时机**：
- 启动 Activity 前
- Activity 生命周期回调时
- 查询任务栈时

---

## 关键方法说明

### 启动相关方法

#### `startActivityLocked()`
- **作用**: Activity 启动的主入口
- **参数**: userId, intent, resolvedType, resultTo, resultWho, requestCode, flags, options
- **返回**: 启动结果码

#### `startActivityInNewTaskLocked()`
- **作用**: 在新任务中启动 Activity
- **流程**: 创建 ActivityRecord → 启动进程 → 生成代理 Intent → 启动

#### `startActivityInSourceTask()`
- **作用**: 在现有任务中启动 Activity
- **流程**: 创建 ActivityRecord → 启动进程 → 生成代理 Intent → 通过 AMS 启动

#### `startActivityProcess()`
- **作用**: 启动目标进程并生成代理 Intent
- **返回**: 代理 Intent

#### `getStartStubActivityIntentInner()`
- **作用**: 生成代理 Activity Intent
- **关键**: 替换 ComponentName，保存原始信息

### 查找相关方法

#### `findActivityRecordByToken()`
- **作用**: 根据 Token 查找 ActivityRecord
- **用途**: 生命周期回调时定位 Activity

#### `findActivityRecordByComponentName()`
- **作用**: 根据 ComponentName 查找 ActivityRecord
- **用途**: 启动模式处理时查找现有 Activity

#### `findTaskRecordByTaskAffinityLocked()`
- **作用**: 根据 taskAffinity 查找 TaskRecord
- **用途**: 启动时查找现有任务栈

#### `findTaskRecordByTokenLocked()`
- **作用**: 根据 Token 查找 TaskRecord
- **用途**: 通过 Activity Token 定位任务栈

### 生命周期相关方法

#### `onActivityCreated()`
- **作用**: Activity 创建回调
- **操作**: 创建/获取 TaskRecord，关联 ProcessRecord

#### `onActivityResumed()`
- **作用**: Activity 恢复回调
- **操作**: 将 Activity 移到栈顶

#### `onActivityDestroyed()`
- **作用**: Activity 销毁回调
- **操作**: 标记 finished，从栈移除

#### `onFinishActivity()`
- **作用**: Activity 完成回调
- **操作**: 标记 finished

### 辅助方法

#### `deliverNewIntentLocked()`
- **作用**: 传递新 Intent 给 Activity
- **调用**: 启动模式复用 Activity 时

#### `finishAllActivity()`
- **作用**: 完成所有标记为 finished 的 Activity
- **调用**: CLEAR_TASK 时

#### `newActivityRecord()`
- **作用**: 创建新的 ActivityRecord
- **操作**: 添加到启动集合，设置超时

#### `synchronizeTasks()`
- **作用**: 同步系统任务栈
- **调用**: 启动前、生命周期回调时

---

## 总结

`ActivityStack` 通过以下机制实现 Activity 管理：

1. **任务栈管理**: 使用 TaskRecord 管理任务，ActivityRecord 管理 Activity
2. **代理替换**: 通过 ProxyActivity 实现 Activity 虚拟化
3. **启动模式**: 支持 standard、singleTop、singleTask、singleInstance
4. **Intent Flags**: 处理 CLEAR_TOP、NEW_TASK、CLEAR_TASK 等
5. **生命周期**: 管理 Activity 的创建、恢复、销毁
6. **任务同步**: 与系统 ActivityManager 同步任务栈状态

这种设计实现了多用户环境下的 Activity 虚拟化，确保每个用户的 Activity 在独立的进程和任务栈中运行。
