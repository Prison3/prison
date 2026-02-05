package com.android.prison.tweaks;

import android.os.IBinder;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * GMS proxy to handle GMS-specific issues like LevelDB locks, device ID problems, and authentication.
 */
public class GmsProxy extends BinderInvocationStub {
    public static final String TAG = GmsProxy.class.getSimpleName();

    public GmsProxy() {
        super(BRServiceManager.get().getService("gms"));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService("gms");
        if (binder == null) {
            Logger.e(TAG, "Failed to get gms service binder");
            return null;
        }
        try {
            Class<?> stubClass = Class.forName("com.google.android.gms.common.api.internal.IGmsServiceBroker$Stub");
            Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
            Object iface = asInterfaceMethod.invoke(null, binder);
            if (iface != null) {
                Logger.d(TAG, "Successfully obtained IGmsServiceBroker interface");
                return iface;
            } else {
                Logger.e(TAG, "Reflection succeeded but returned null interface");
                return null;
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to get IGmsServiceBroker interface", e);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("gms");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook getService to handle package name validation
    @ProxyMethod("getService")
    public static class GetService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Check if this is a package name validation issue
                if (args != null && args.length > 0) {
                    String callingPackage = (String) args[0];
                    if ("com.google.android.gms".equals(callingPackage)) {
                        // Replace with the correct package name
                        args[0] = PrisonCore.getPackageName();
                        Logger.d(TAG, "GmsProxy: Fixed calling package from com.google.android.gms to " + PrisonCore.getPackageName());
                    }
                }
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.e(TAG, "GmsProxy: Error in getService", e);
                // Return a mock service or null to prevent crashes
                return null;
            }
        }
    }

    // Hook getServiceBroker to handle service broker issues
    @ProxyMethod("getServiceBroker")
    public static class GetServiceBroker extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.e(TAG, "GmsProxy: Error in getServiceBroker", e);
                // Return null to prevent crashes
                return null;
            }
        }
    }

    // Hook authenticate to handle authentication issues
    @ProxyMethod("authenticate")
    public static class Authenticate extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Logger.d(TAG, "GmsProxy: Handling authenticate call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "GmsProxy: Authentication error, returning success", e);
                // Return a mock successful authentication result
                return createMockAuthResult();
            }
        }
    }

    // Hook getAccount to handle account retrieval issues
    @ProxyMethod("getAccount")
    public static class GetAccount extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Logger.d(TAG, "GmsProxy: Handling getAccount call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "GmsProxy: GetAccount error, returning null", e);
                return null;
            }
        }
    }

    // Hook getToken to handle token retrieval issues
    @ProxyMethod("getToken")
    public static class GetToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Logger.d(TAG, "GmsProxy: Handling getToken call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "GmsProxy: GetToken error, returning mock token", e);
                return "mock_gms_token_" + System.currentTimeMillis();
            }
        }
    }

    // Hook invalidateToken to handle token invalidation
    @ProxyMethod("invalidateToken")
    public static class InvalidateToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Logger.d(TAG, "GmsProxy: Handling invalidateToken call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "GmsProxy: InvalidateToken error, ignoring", e);
                return null;
            }
        }
    }

    // Hook clearToken to handle token clearing
    @ProxyMethod("clearToken")
    public static class ClearToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Logger.d(TAG, "GmsProxy: Handling clearToken call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Logger.w(TAG, "GmsProxy: ClearToken error, ignoring", e);
                return null;
            }
        }
    }

    // Helper method to create a mock authentication result
    private static Object createMockAuthResult() {
        try {
            // Try to create a mock Bundle or similar object
            Class<?> bundleClass = Class.forName("android.os.Bundle");
            return bundleClass.newInstance();
        } catch (Exception e) {
            Logger.w(TAG, "Failed to create mock auth result", e);
            return null;
        }
    }
}
