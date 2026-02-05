package com.android.prison.base;

import java.lang.reflect.Method;

import com.android.prison.core.PrisonCore;

/**
 * Abstract base class for method hooks.
 * Provides a framework for intercepting and modifying method calls.
 */
public abstract class MethodHook {
    /**
     * Get the method name to hook.
     * Override this method to specify which method to hook.
     * 
     * @return The method name, or null to use the default
     */
    protected String getMethodName() {
        return null;
    }

    /**
     * Called after the original method is executed.
     * Override this method to modify the return value.
     * 
     * @param result The original method's return value
     * @return The modified return value
     * @throws Throwable If an error occurs
     */
    protected Object afterHook(Object result) throws Throwable {
        return result;
    }

    /**
     * Called before the original method is executed.
     * Override this method to modify arguments or skip the original method.
     * 
     * @param who The object instance (null for static methods)
     * @param method The method being hooked
     * @param args The method arguments
     * @return If non-null, this value will be returned instead of calling the original method
     * @throws Throwable If an error occurs
     */
    protected Object beforeHook(Object who, Method method, Object[] args) throws Throwable {
        return null;
    }

    /**
     * The main hook method that intercepts the method call.
     * This method must be implemented by subclasses.
     * 
     * @param who The object instance (null for static methods)
     * @param method The method being hooked
     * @param args The method arguments
     * @return The return value for the method call
     * @throws Throwable If an error occurs
     */
    protected abstract Object hook(Object who, Method method, Object[] args) throws Throwable;

    /**
     * Check if this hook is enabled.
     * By default, hooks are only enabled in Prison processes.
     * 
     * @return true if the hook is enabled, false otherwise
     */
    protected boolean isEnable() {
        return PrisonCore.get().isPrisonProcess();
    }
}
