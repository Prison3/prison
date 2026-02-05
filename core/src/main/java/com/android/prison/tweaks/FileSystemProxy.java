package com.android.prison.tweaks;

import java.io.File;
import java.lang.reflect.Method;

import com.android.prison.base.ClassInvocationStub;
import com.android.prison.base.MethodHook;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * FileSystem proxy to handle file and directory creation issues.
 */
public class FileSystemProxy extends ClassInvocationStub {
    public static final String TAG = FileSystemProxy.class.getSimpleName();

    public FileSystemProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null; // Not needed for class method hooks
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Hook File class methods directly
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook mkdirs to handle directory creation issues
    @ProxyMethod("mkdirs")
    public static class Mkdirs extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                File file = (File) who;
                String path = file.getAbsolutePath();
                
                // Handle Helium crash reporter paths
                if (path.contains("Helium Crashpad") || path.contains("HeliumCrashReporter")) {
                    Logger.d(TAG, "FileSystem: mkdirs called for Helium crash path: " + path + ", returning true");
                    return true; // Pretend directory was created successfully
                }
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "FileSystem: mkdirs failed, returning true", e);
                return true; // Return success to prevent crashes
            }
        }
    }

    // Hook mkdir to handle directory creation issues
    @ProxyMethod("mkdir")
    public static class Mkdir extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                File file = (File) who;
                String path = file.getAbsolutePath();
                
                // Handle Helium crash reporter paths
                if (path.contains("Helium Crashpad") || path.contains("HeliumCrashReporter")) {
                    Logger.d(TAG, "FileSystem: mkdir called for Helium crash path: " + path + ", returning true");
                    return true; // Pretend directory was created successfully
                }
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "FileSystem: mkdir failed, returning true", e);
                return true; // Return success to prevent crashes
            }
        }
    }

    // Hook isDirectory to handle directory check issues
    @ProxyMethod("isDirectory")
    public static class IsDirectory extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                File file = (File) who;
                String path = file.getAbsolutePath();
                
                // Handle Helium crash reporter paths
                if (path.contains("Helium Crashpad") || path.contains("HeliumCrashReporter")) {
                    Logger.d(TAG, "FileSystem: isDirectory called for Helium crash path: " + path + ", returning true");
                    return true; // Pretend it's a directory
                }
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "FileSystem: isDirectory failed, returning false", e);
                return false; // Return false to prevent crashes
            }
        }
    }
}
