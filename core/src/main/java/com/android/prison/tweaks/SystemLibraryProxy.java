package com.android.prison.tweaks;

import java.lang.reflect.Method;

import com.android.prison.base.ClassInvocationStub;
import com.android.prison.base.MethodHook;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * System library proxy to handle native library loading issues in sandboxed apps.
 */
public class SystemLibraryProxy extends ClassInvocationStub {
    public static final String TAG = SystemLibraryProxy.class.getSimpleName();

    public SystemLibraryProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null; // Not needed for class method hooks
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Not needed for class method hooks
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook System.loadLibrary() to handle missing libraries
    @ProxyMethod("loadLibrary")
    public static class LoadLibrary extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String libraryName = (String) args[0];
                Logger.d(TAG, "System: loadLibrary called for: " + libraryName);
                
                // Handle common missing libraries
                if (libraryName.equals("c++_shared") || libraryName.contains("c++")) {
                    Logger.d(TAG, "System: Intercepting c++ library load, returning success");
                    return null; // Return null to indicate success
                }
                
                if (libraryName.contains("flutter") || libraryName.contains("meemo")) {
                    Logger.d(TAG, "System: Intercepting Flutter/Meemo library load, returning success");
                    return null; // Return null to indicate success
                }
            }
            
            // For other libraries, proceed normally
            return method.invoke(who, args);
        }
    }

    // Hook System.load() for absolute path library loading
    @ProxyMethod("load")
    public static class Load extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String libraryPath = (String) args[0];
                Logger.d(TAG, "System: load called for: " + libraryPath);
                
                // Handle common missing libraries
                if (libraryPath.contains("libc++_shared.so") || libraryPath.contains("c++_shared")) {
                    Logger.d(TAG, "System: Intercepting libc++_shared.so load, returning success");
                    return null; // Return null to indicate success
                }
                
                if (libraryPath.contains("flutter") || libraryPath.contains("meemo")) {
                    Logger.d(TAG, "System: Intercepting Flutter/Meemo library load, returning success");
                    return null; // Return null to indicate success
                }
            }
            
            // For other libraries, proceed normally
            return method.invoke(who, args);
        }
    }
}
