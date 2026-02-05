package com.android.prison.tweaks;

import java.lang.reflect.Method;

import com.android.prison.base.ClassInvocationStub;
import com.android.prison.base.MethodHook;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.AttributionSourceUtils;
import com.android.prison.utils.Logger;

/**
 * ISettingsSystem Proxy to handle AttributionSource UID issues on Android 12+
 * This prevents crashes related to Settings.System UID enforcement
 */
public class ISettingsSystemProxy extends ClassInvocationStub {
    public static final String TAG = ISettingsSystemProxy.class.getSimpleName();

    public ISettingsSystemProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        // Return null since this is a global hook, not a specific service
        return null;
    }

    @Override
    protected void inject(Object base, Object proxy) {
        // This proxy handles Settings.System calls globally
        Logger.d(TAG, "ISettingsSystem proxy initialized for UID mismatch prevention");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook for getStringForUser method to fix AttributionSource issues
    @ProxyMethod("getStringForUser")
    public static class GetStringForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Fix AttributionSource in args before calling original method
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                // Call original method
                return method.invoke(who, args);
            } catch (SecurityException e) {
                // Handle SecurityException specifically for UID mismatch
                String message = e.getMessage();
                if (message != null && message.contains("Calling uid") && message.contains("doesn't match source uid")) {
                    Logger.w(TAG, "UID mismatch in getStringForUser, returning safe default: " + message);
                    return ""; // Return empty string to prevent crashes
                }
                throw e;
            } catch (Exception e) {
                Logger.w(TAG, "Error in getStringForUser hook: " + e.getMessage());
                // Return empty string to prevent crashes
                return "";
            }
        }
    }

    // Hook for getString method to fix AttributionSource issues
    @ProxyMethod("getString")
    public static class GetString extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Fix AttributionSource in args before calling original method
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                // Call original method
                return method.invoke(who, args);
            } catch (SecurityException e) {
                // Handle SecurityException specifically for UID mismatch
                String message = e.getMessage();
                if (message != null && message.contains("Calling uid") && message.contains("doesn't match source uid")) {
                    Logger.w(TAG, "UID mismatch in getString, returning safe default: " + message);
                    return ""; // Return empty string to prevent crashes
                }
                throw e;
            } catch (Exception e) {
                Logger.w(TAG, "Error in getString hook: " + e.getMessage());
                // Return empty string to prevent crashes
                return "";
            }
        }
    }

    // Hook for putString method to fix AttributionSource issues
    @ProxyMethod("putString")
    public static class PutString extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Fix AttributionSource in args before calling original method
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                // Call original method
                return method.invoke(who, args);
            } catch (SecurityException e) {
                // Handle SecurityException specifically for UID mismatch
                String message = e.getMessage();
                if (message != null && message.contains("Calling uid") && message.contains("doesn't match source uid")) {
                    Logger.w(TAG, "UID mismatch in putString, returning false: " + message);
                    return false; // Return false to prevent crashes
                }
                throw e;
            } catch (Exception e) {
                Logger.w(TAG, "Error in putString hook: " + e.getMessage());
                // Return false to prevent crashes
                return false;
            }
        }
    }
}
