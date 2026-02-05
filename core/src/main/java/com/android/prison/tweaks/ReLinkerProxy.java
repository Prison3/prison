package com.android.prison.tweaks;

import java.lang.reflect.Method;

import com.android.prison.base.ClassInvocationStub;
import com.android.prison.base.MethodHook;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * ReLinker proxy to handle com.getkeepsafe.relinker library loading issues.
 */
public class ReLinkerProxy extends ClassInvocationStub {
    public static final String TAG = ReLinkerProxy.class.getSimpleName();

    public ReLinkerProxy() {
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

    // Hook ReLinker.loadLibrary() to handle missing libraries
    @ProxyMethod("loadLibrary")
    public static class LoadLibrary extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "ReLinker: loadLibrary called, intercepting to prevent MissingLibraryException");
            
            // Return null to indicate success without actually loading the library
            // This prevents the MissingLibraryException from being thrown
            return null;
        }
    }

    // Hook ReLinker.loadLibrary() with context parameter
    @ProxyMethod("loadLibrary")
    public static class LoadLibraryWithContext extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 1) {
                String libraryName = (String) args[1];
                Logger.d(TAG, "ReLinker: loadLibrary called for: " + libraryName);
            }
            
            // Return null to indicate success
            return null;
        }
    }

    // Hook ReLinker.loadLibrary() with all parameters
    @ProxyMethod("loadLibrary")
    public static class LoadLibraryWithAllParams extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 2) {
                String libraryName = (String) args[1];
                String version = (String) args[2];
                Logger.d(TAG, "ReLinker: loadLibrary called for: " + libraryName + " version: " + version);
            }
            
            // Return null to indicate success
            return null;
        }
    }
}
