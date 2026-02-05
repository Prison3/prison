# ActivityManagerCommonProxy vs. Original startActivity Differences

## Overview

`ActivityManagerCommonProxy.StartActivity` is a proxy implementation that hooks the Android system
`IActivityManager.startActivity()` method. It intercepts all Activity launch requests from apps,
applies special handling in the virtualized environment, and then forwards to the virtualized
ActivityManagerService.

## Original startActivity Flow

Typical flow of the original `startActivity` method in Android (inside `ActivityManagerService`):

```
1. Receive Intent parameters
2. Verify caller permissions
3. Resolve Intent via system PackageManager
4. Check target Activity existence
5. Validate permissions and launch mode
6. Create ActivityRecord
7. Start target process (if needed)
8. Launch target Activity
```

**Characteristics**:
- Uses the system PackageManager directly
- Launches real Activities directly
- No virtualization isolation
- No package name replacement

## Differences in the Hooked Version

### 1. Package Name Replacement (line 36)

```java
MethodParameterUtils.replaceFirstAppPkg(args);
```

**Purpose**: Replace the virtual app package name in parameters with the host app package name.

**Reason**: In a virtualized environment, apps see a virtual package name, but system services expect
the host package name.

**Original behavior**: Uses the incoming package name directly.

---

### 2. Proxy Intent Check (lines 42-44)

```java
if (intent.getParcelableExtra("_B_|_target_") != null) {
    return method.invoke(who, args);
}
```

**Purpose**: If the Intent already has the `_B_|_target_` marker, it is a proxy Intent and the
original method is invoked directly.

**Reason**: Avoid double-processing proxy Intents and prevent loops.

**Original behavior**: No such check; all Intents go through the normal flow.

---

### 3. Install Request Handling (lines 45-71)

```java
if (ComponentUtils.isRequestInstall(intent)) {
    // handle install logic
}
```

**Purpose**:
- Detect install requests (`application/vnd.android.package-archive`)
- Block installing the Prison host app itself (lines 48-64)
- If allowed, convert FileProvider URI (line 69)

**Key logic**:

#### 3.1 Block installing Prison itself
```java
// Check if attempting to install Prison app
if (packageName.equals(hostPackageName)) {
    Logger.w(TAG, "Blocked attempt to install Prison app from within Prison");
    return 0;  // return success but do not install
}
```

**Reason**: Prevent installing the host app from within the virtualized app, which may destabilize
the system.

**Original behavior**: The system processes all install requests without this protection.

#### 3.2 Forward install requests
```java
if (PrisonCore.get().getSettings().requestInstallPackage(file, userId)) {
    return 0;  // handled by the virtualization system
}
```

**Reason**: In a virtualized environment, install requests require special handling (user consent
or permission checks).

**Original behavior**: Calls the system installer directly.

---

### 4. Package URI Redirection (lines 72-75)

```java
String dataString = intent.getDataString();
if (dataString != null && dataString.equals("package:" + PActivityThread.getAppPackageName())) {
    intent.setData(Uri.parse("package:" + PrisonCore.getPackageName()));
}
```

**Purpose**: Redirect `package:` URIs pointing to a virtual app package to the host app package.

**Reason**: System settings pages need to show host app details rather than the virtual app.

**Original behavior**: Uses the Intent URI directly without redirection.

**Example**:
- Virtual app package: `com.example.app`
- Host app package: `com.android.prison`
- Conversion: `package:com.example.app` → `package:com.android.prison`

---

### 5. Resolve via Virtualized PackageManager (lines 77-98)

```java
ResolveInfo resolveInfo = PPackageManager.get().resolveActivity(
    intent,
    GET_META_DATA,
    StartActivityCompat.getResolvedType(args),
    PActivityThread.getUserId());
```

**Purpose**: Use the virtualized `PPackageManager` instead of the system `PackageManager` to resolve
the Activity.

**Key differences**:

#### 5.1 First resolve (lines 77-81)
- Uses the virtualized PackageManager
- Looks up components in the virtual app user space

#### 5.2 Fallback (lines 82-98)
If the first resolve fails:
```java
if (resolveInfo == null) {
    // If no package/component specified, try virtual package name
    if (intent.getPackage() == null && intent.getComponent() == null) {
        intent.setPackage(PActivityThread.getAppPackageName());
    }
    // Try resolve again
    resolveInfo = PPackageManager.get().resolveActivity(...);
    // If still fails, restore original package and call original method
    if (resolveInfo == null) {
        intent.setPackage(origPackage);
        return method.invoke(who, args);
    }
}
```

**Reason**:
- Virtual environment must resolve in its own package space
- Supports implicit Intents (no component specified)
- Fall back to system behavior if not found

**Original behavior**:
- Uses system `PackageManager.resolveActivity()`
- Resolves in the global system package space
- No user space isolation

---

### 6. Set ComponentName (lines 101-102)

```java
intent.setExtrasClassLoader(who.getClass().getClassLoader());
intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
```

**Purpose**:
- Set the correct ClassLoader (to load virtual app classes)
- Set the Intent ComponentName to the resolved Activity

**Reason**:
- Virtualization requires correct class loading
- Ensure the Intent points to the correct component

**Original behavior**:
- The system sets ClassLoader automatically
- ComponentName is resolved by the system

---

### 7. Call Virtualized ActivityManagerService (lines 103-111)

```java
PActivityManager.get().startActivityAms(PActivityThread.getUserId(),
    StartActivityCompat.getIntent(args),
    StartActivityCompat.getResolvedType(args),
    StartActivityCompat.getResultTo(args),
    StartActivityCompat.getResultWho(args),
    StartActivityCompat.getRequestCode(args),
    StartActivityCompat.getFlags(args),
    StartActivityCompat.getOptions(args));
return 0;
```

**Purpose**: Call the virtualized `ActivityManagerService.startActivityAms()` instead of the system
method.

**Key differences**:

| Item | Original | Hooked |
|------|----------|--------|
| Call target | `ActivityManagerService.startActivity()` | `PActivityManager.startActivityAms()` |
| User space | System global | Virtual user space (userId) |
| Activity stack | System ActivityStack | Virtual ActivityStack |
| Process management | System process manager | Virtual process manager |
| Proxy replacement | None | Replaced with ProxyActivity |

**Reason**:
- Virtual environment needs an independent Activity stack
- Requires Activity proxy replacement mechanism
- Needs user space isolation

**Original behavior**:
- Calls system `ActivityManagerService`
- Uses the system global Activity stack
- Launches real Activities directly

---

## Full Flow Comparison

### Original startActivity Flow

```
App calls startActivity()
    ↓
System ActivityManagerService.startActivity()
    ↓
System PackageManager.resolveActivity()
    ↓
Create ActivityRecord
    ↓
Start process (if needed)
    ↓
Launch real Activity
```

### Hooked Flow

```
App calls startActivity()
    ↓
ActivityManagerCommonProxy.StartActivity.hook()
    ↓
Replace package name parameters
    ↓
Check proxy Intent → if yes, call original method
    ↓
Check install request → special handling
    ↓
Package URI redirection
    ↓
Virtualized PackageManager.resolveActivity()
    ↓
Set ComponentName and ClassLoader
    ↓
PActivityManager.startActivityAms()
    ↓
Virtual ActivityManagerService.startActivityAms()
    ↓
Virtual ActivityStack.startActivityLocked()
    ↓
Replace with ProxyActivity
    ↓
Launch ProxyActivity → restore original Activity
```

---

## Key Design Points

### 1. Transparent Proxying
The hook is transparent to app code; no app changes are required.

### 2. Fallback
If the virtualized environment cannot handle a request, it falls back to the system method.

### 3. Safety Checks
- Block installing the host app
- Verify component existence
- Permission checks (in other hooks)

### 4. Package Name Mapping
- Virtual package name ↔ host package name
- Automatic conversion and redirection

### 5. User Space Isolation
- Each virtual app has its own userId
- Independent Activity stacks
- Independent package management space

---

## Code Examples

### Original Call Example

```java
// App code
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setData(Uri.parse("package:com.example.app"));
startActivity(intent);

// System handling
ActivityManagerService.startActivity(intent) {
    // Use system PackageManager directly
    ResolveInfo info = PackageManager.resolveActivity(intent);
    // Launch real Activity directly
    startRealActivity(info.activityInfo);
}
```

### Hooked Handling Example

```java
// App code (same)
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setData(Uri.parse("package:com.example.app"));
startActivity(intent);

// Hook handling
ActivityManagerCommonProxy.StartActivity.hook() {
    // 1. Replace package name parameters
    replaceFirstAppPkg(args);
    
    // 2. Package URI redirection
    if (dataString.equals("package:com.example.app")) {
        intent.setData(Uri.parse("package:com.android.prison"));
    }
    
    // 3. Virtualized resolve
    ResolveInfo info = PPackageManager.resolveActivity(intent, userId);
    
    // 4. Set ComponentName
    intent.setComponent(new ComponentName(info.packageName, info.name));
    
    // 5. Call virtualized service
    PActivityManager.startActivityAms(userId, intent, ...);
    
    // 6. Virtualized service replaces with ProxyActivity
    // 7. ProxyActivity restores the original Activity
}
```

---

## Summary

Main differences between `ActivityManagerCommonProxy.StartActivity` and the original `startActivity`:

1. **Package name replacement**: virtual package ↔ host package
2. **Proxy check**: avoid double-processing proxy Intents
3. **Install protection**: block installing the host app
4. **URI redirection**: redirect package URIs to the host app
5. **Virtualized resolve**: use virtualized PackageManager
6. **Fallback**: fall back to system handling if resolve fails
7. **Virtualized start**: call virtualized ActivityManagerService
8. **Proxy replacement**: eventually replaced with ProxyActivity

These differences enable the virtualized environment to:
- Transparently intercept all Activity launch requests
- Provide multi-user isolation
- Implement Activity proxy replacement
- Remain compatible with the original system behavior

---

## Related Documents

- [Virtualization of Android's Four Core Components](./Android四大组件虚拟化工作原理.en.md)
- [ActivityStack Internals](./ActivityStack工作原理.en.md)
- [ActivityManagerService Internals](./ActivityManagerService工作原理.en.md)
