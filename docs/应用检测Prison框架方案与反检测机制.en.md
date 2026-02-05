# App Detection vs. Prison and Anti-Detection Mechanisms

## Contents

1. [Overview](#overview)
2. [Detection Categories](#detection-categories)
3. [File System Detection](#file-system-detection)
4. [Package and Process Detection](#package-and-process-detection)
5. [Class Loading Detection](#class-loading-detection)
6. [Stack Trace Detection](#stack-trace-detection)
7. [System Property Detection](#system-property-detection)
8. [System Service Detection](#system-service-detection)
9. [Prison Anti-Detection Mechanisms](#prison-anti-detection-mechanisms)
10. [Summary](#summary)

---

## Overview

Apps try to detect virtualization frameworks to restrict features or block execution. Prison uses
multi-layer anti-detection techniques to hide its presence and preserve normal app behavior.

```
App Detection  ↔  Prison Anti-Detection
    ↓                 ↓
Identify sandbox      Hide sandbox
    ↓                 ↓
Block/limit features  Provide normal service
```

---

## Detection Categories

### 1. Static Detection
- File system probing (directories, artifacts)
- Package name checks
- Class loading checks

### 2. Dynamic Detection
- Process name checks
- Stack trace inspection
- System service return values

### 3. Runtime Detection
- System property checks
- UID/GID checks
- Network/proxy configuration checks

---

## File System Detection

### Typical Checks

Apps probe common virtualization paths:

```java
String[] virtualPaths = {
    "/data/virtual",
    "/data/data/com.lody.virtual",
    "/data/data/com.android.prisona",
    "/prison",
    "/virtual"
};
```

They may also check root/emulator traces:

```java
String[] rootFiles = {
    "/system/xbin/su",
    "/system/bin/su",
    "/sbin/su"
};
```

### Prison Countermeasures

Native hooks intercept file APIs and return `ENOENT` for blocked paths:

```cpp
static int my_access(const char *pathname, int mode) {
    if (pathname && (is_blocked_file(pathname) || is_blocked_package(pathname))) {
        errno = ENOENT;
        return -1;
    }
    return orig_access(pathname, mode);
}
```

Hooks cover `access()`, `stat()`, `open()`, `fopen()`, `readlink()`, `opendir()` and others.

---

## Package and Process Detection

### Typical Checks

Apps enumerate installed packages or processes:

```java
for (PackageInfo pkg : pm.getInstalledPackages(0)) {
    if (pkg.packageName.contains("virtual") ||
        pkg.packageName.contains("prison")) {
        return true;
    }
}
```

### Prison Countermeasures

- Package list filtering in virtual package manager
- Process name normalization to host-visible names
- UID/AttributionSource adjustments for consistency

---

## Class Loading Detection

### Typical Checks

Apps search for framework classes via reflection or ClassLoader:
- `XposedBridge`
- virtual framework classes
- custom classloader patterns

### Prison Countermeasures

Prison intercepts class loading (VMClassLoader/DexFile hooks) and filters results for target apps.

---

## Stack Trace Detection

### Typical Checks

Apps inspect stack traces for framework class names and suspicious call paths.

### Prison Countermeasures

Stack trace filtering removes virtualization-related frames in key paths.

---

## System Property Detection

### Typical Checks

Apps read system properties to infer emulator/virtualized environments.

### Prison Countermeasures

Property spoofing and selective filtering in native layer and Java side.

---

## System Service Detection

### Typical Checks

Apps query system services for inconsistencies:
- Activity/Task info
- Package manager behavior
- ContentProvider permissions

### Prison Countermeasures

System service proxies normalize outputs and enforce sandbox-specific logic.

---

## Prison Anti-Detection Mechanisms

Prison combines:
- Native file access hooks
- Package/process filtering
- Class loader & Dex file hooks
- Stack trace sanitization
- System property spoofing
- Service proxy normalization

These techniques reduce the signal for virtualization detection while preserving app functionality.

---

## Summary

App detection and anti-detection is an ongoing arms race. Prison provides layered defenses across
file system access, package/process visibility, class loading, stack trace inspection, system
properties, and service APIs to keep virtualization transparent and stable.
