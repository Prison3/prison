# InjectorManager Proxy Class Reference

## Overview

`InjectorManager` is the core injection manager in the virtualization framework. It manages and
injects all system service proxy classes. These proxies hook and redirect system service calls so
virtualized apps can run in a multi-user environment.

This document summarizes the proxy categories registered in `InjectorManager.java` and the
responsibilities of representative proxies. For exact class lists and implementation details, refer
to `core/src/main/java/com/android/prison/manager/InjectorManager.java`.

---

## Contents

1. [Core System Service Proxies](#core-system-service-proxies)
2. [Activity and Task Management Proxies](#activity-and-task-management-proxies)
3. [Package and Permission Proxies](#package-and-permission-proxies)
4. [Notification and Alarm Proxies](#notification-and-alarm-proxies)
5. [Network and Connectivity Proxies](#network-and-connectivity-proxies)
6. [Storage and File System Proxies](#storage-and-file-system-proxies)
7. [Media and Audio Proxies](#media-and-audio-proxies)
8. [Location and Sensor Proxies](#location-and-sensor-proxies)
9. [User and Account Proxies](#user-and-account-proxies)
10. [Device ID and Hardware Proxies](#device-id-and-hardware-proxies)
11. [WebView and Browser Proxies](#webview-and-browser-proxies)
12. [Database and Persistence Proxies](#database-and-persistence-proxies)
13. [Security and Policy Proxies](#security-and-policy-proxies)
14. [MIUI-Specific Proxies](#miui-specific-proxies)
15. [Other Utility Proxies](#other-utility-proxies)

---

## Core System Service Proxies

### IDisplayManagerProxy
**Role**: Hook `IDisplayManager` to virtualize display queries and configuration.

**Key use cases**:
- Screen size/DPI queries in sandbox
- Multi-display compatibility

### OsStub
**Role**: Hook `libcore.io.Os` to intercept low-level syscalls (file, process, network).

**Key use cases**:
- File path redirection
- Process and thread virtualization
- Network syscall interception

### ContentServiceStub / ContentResolverProxy
**Role**: Virtualize content observer registration and ContentResolver CRUD access, ensuring
permissions and provider routing are handled within the sandbox.

---

## Activity and Task Management Proxies

### IActivityManagerProxy
**Role**: Central proxy for `IActivityManager` to redirect Activity/Service/Broadcast operations to
the virtual manager (`PActivityManager`).

**Key capabilities**:
- Activity lifecycle handling
- Service start/bind/unbind routing
- Broadcast send/register/finish routing
- IntentSender and PendingIntent mediation

### HCallbackProxy
**Role**: Hook `ActivityThread.mH` to intercept launch/create messages and restore real component
info from proxy Intents.

---

## Package and Permission Proxies

### IPackageManagerProxy
**Role**: Virtualize package queries, signatures, permissions, and component resolution.

### IAppOpsManagerProxy / IPermissionManagerProxy
**Role**: Normalize app-ops checks and permission enforcement for sandboxed apps.

---

## Notification and Alarm Proxies

### INotificationManagerProxy
**Role**: Route notifications to virtual context and avoid UID mismatches.

### IAlarmManagerProxy
**Role**: Virtualize alarm scheduling and identity.

---

## Network and Connectivity Proxies

### IConnectivityManagerProxy / IDnsResolverProxy
**Role**: Normalize network capability/query responses for sandboxed apps.

### IVpnManagerProxy
**Role**: Manage VPN interaction inside the virtual environment.

---

## Storage and File System Proxies

### IStorageManagerProxy / FileSystemProxy
**Role**: Redirect paths and enforce sandbox boundaries for storage operations.

### ApkAssetsProxy / ResourcesManagerProxy
**Role**: Adjust asset/resource loading for virtualized apps.

---

## Media and Audio Proxies

### IAudioServiceProxy / AudioRecordProxy / MediaRecorderProxy
**Role**: Normalize audio recording and media access for sandboxed apps.

---

## Location and Sensor Proxies

### ILocationManagerProxy / ISensorPrivacyManagerProxy
**Role**: Route location/sensor calls and enforce sandbox permissions.

---

## User and Account Proxies

### IUserManagerProxy / IAccountManagerProxy / GoogleAccountManagerProxy
**Role**: Map user/account queries to virtual user space.

---

## Device ID and Hardware Proxies

### DeviceIdProxy / AndroidIdProxy / IPhoneSubInfoProxy
**Role**: Provide virtualized device identifiers to avoid real device leakage.

---

## WebView and Browser Proxies

### WebViewProxy / WebViewFactoryProxy / IWebViewUpdateServiceProxy
**Role**: Virtualize WebView provider selection and update flows.

---

## Database and Persistence Proxies

### SQLiteDatabaseProxy / LevelDbProxy
**Role**: Redirect database file access and isolate storage.

---

## Security and Policy Proxies

### IDevicePolicyManagerProxy / IAppOpsManagerProxy
**Role**: Normalize policy checks and enforce virtualized permissions.

---

## MIUI-Specific Proxies

MIUI devices expose private services and strict policies. The MIUI-specific proxies prevent crashes
and normalize behaviors on Xiaomi ROMs (e.g., MIUI security/setting services).

---

## Other Utility Proxies

Includes compatibility wrappers, classloader proxies, and vendor-specific shims used to improve
app compatibility in the sandbox.

---

## Notes

- The proxy list is large (85+). Refer to `InjectorManager.java` for the definitive list.
- Each proxy typically hooks a system binder interface or a static class API to redirect calls.
- Proxies are injected only in Prison processes to avoid impacting the host outside the sandbox.
