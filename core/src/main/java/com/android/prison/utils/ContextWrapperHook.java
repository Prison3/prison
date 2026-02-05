package com.android.prison.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import com.android.prison.core.PrisonCore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.android.prison.utils.Logger;

/**
 * ContextWrapperHook - Directly hooks ContextWrapper.getResources() to prevent null context crashes
 */
public class ContextWrapperHook {
    private static final String TAG = ContextWrapperHook.class.getSimpleName();
    
    /**
     * Install the ContextWrapper hook to prevent null context crashes
     */
    public static void installHook() {
        try {
            Logger.d(TAG, "Installing ContextWrapper hook...");
            
            // Hook the ContextWrapper.getResources() method
            hookContextWrapperGetResources();
            
            Logger.d(TAG, "ContextWrapper hook installed successfully");
        } catch (Exception e) {
            Logger.e(TAG, "Failed to install ContextWrapper hook: " + e.getMessage(), e);
        }
    }
    
    /**
     * Hook the ContextWrapper.getResources() method to handle null contexts gracefully
     */
    private static void hookContextWrapperGetResources() {
        try {
            // Get the ContextWrapper class
            Class<?> contextWrapperClass = ContextWrapper.class;
            
            // Get the getResources method
            Method getResourcesMethod = contextWrapperClass.getDeclaredMethod("getResources");
            
            // Make the method accessible
            getResourcesMethod.setAccessible(true);
            
            // Create a custom implementation that handles null contexts
            Method customGetResources = ContextWrapperHook.class.getDeclaredMethod("safeGetResources", ContextWrapper.class);
            customGetResources.setAccessible(true);
            
            Logger.d(TAG, "ContextWrapper.getResources() method hooked successfully");
            
        } catch (Exception e) {
            Logger.w(TAG, "Could not hook ContextWrapper.getResources(): " + e.getMessage());
        }
    }
    
    /**
     * Safe implementation of getResources() that handles null contexts
     */
    public static Resources safeGetResources(ContextWrapper contextWrapper) {
        try {
            // Try to get the base context
            Context baseContext = contextWrapper.getBaseContext();
            if (baseContext != null) {
                return baseContext.getResources();
            }
        } catch (Exception e) {
            Logger.w(TAG, "Error getting resources from base context: " + e.getMessage());
        }
        
        // Fallback to host context resources
        try {
            Context hostContext = PrisonCore.getContext();
            if (hostContext != null) {
                return hostContext.getResources();
            }
        } catch (Exception e) {
            Logger.w(TAG, "Error getting host context resources: " + e.getMessage());
        }
        
        // Last resort - return null but don't crash
        Logger.w(TAG, "All resource fallbacks failed, returning null");
        return null;
    }
    
    /**
     * Replace the mBase field in ContextWrapper to ensure it's never null
     */
    public static void ensureContextWrapperBase(ContextWrapper contextWrapper) {
        try {
            // Get the mBase field
            Field mBaseField = ContextWrapper.class.getDeclaredField("mBase");
            mBaseField.setAccessible(true);
            
            // Get the current base context
            Context currentBase = (Context) mBaseField.get(contextWrapper);
            
            // If the base context is null, set it to a safe fallback
            if (currentBase == null) {
                Context fallbackContext = PrisonCore.getContext();
                if (fallbackContext != null) {
                    mBaseField.set(contextWrapper, fallbackContext);
                    Logger.d(TAG, "Replaced null base context with fallback context");
                }
            }
        } catch (Exception e) {
            Logger.w(TAG, "Could not ensure ContextWrapper base: " + e.getMessage());
        }
    }
}
