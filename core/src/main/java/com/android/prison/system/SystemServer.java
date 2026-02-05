package com.android.prison.system;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.android.prison.core.PrisonCore;
import com.android.prison.proxy.ProxyManifest;
import com.android.prison.utils.BuildCompat;
import com.android.prison.utils.BundleCompat;
import com.android.prison.utils.ProviderCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.prison.utils.Logger;

public class SystemServer {
    private static final String TAG = SystemServer.class.getSimpleName();
    private static final String[] REQUIRED_SERVICES = {
        ServiceManager.ACTIVITY_MANAGER,
        ServiceManager.PACKAGE_MANAGER,
        ServiceManager.STORAGE_MANAGER,
        ServiceManager.USER_MANAGER,
        ServiceManager.JOB_MANAGER,
        ServiceManager.ACCOUNT_MANAGER,
        ServiceManager.LOCATION_MANAGER,
        ServiceManager.NOTIFICATION_MANAGER
    };

    private final Map<String, IBinder> mServices = new HashMap<>();
    private boolean mServicesInitialized = false;
    private final List<Runnable> mServiceAvailableCallbacks = new ArrayList<>();
    private final Object mServiceCallbackLock = new Object();
    private Context mContext;
    private static final SystemServer sSystemServer = new SystemServer();
    public static SystemServer get() {
        return sSystemServer;
    }
    private SystemServer() {
        mContext = PrisonCore.getContext();
    }

    /**
     * Register a callback to be notified when services become available
     */
    public void addServiceAvailableCallback(Runnable callback) {
        synchronized (mServiceCallbackLock) {
            if (mServicesInitialized) {
                // Services are already available, run callback immediately
                callback.run();
            } else {
                mServiceAvailableCallbacks.add(callback);
            }
        }
    }

    /**
     * Initialize services normally, with fallback for each service if needed
     */
    private boolean initializeServices() {
        for (String serviceName : REQUIRED_SERVICES) {
            try {
                getServiceInternal(serviceName);
            } catch (Exception e) {
                Logger.w(TAG, "Failed to initialize service: " + serviceName + ", trying fallback", e);
            }
        }
        
        markServicesInitialized("Services initialized successfully");
        return true;
    }

    /**
     * Mark services as initialized and notify callbacks
     */
    private void markServicesInitialized(String message) {
        mServicesInitialized = true;
        Logger.d(TAG, message);
        notifyServiceAvailableCallbacks();
    }
    /**
     * Notify all registered callbacks that services are available
     */
    private void notifyServiceAvailableCallbacks() {
        synchronized (mServiceCallbackLock) {
            if (!mServiceAvailableCallbacks.isEmpty()) {
                Logger.d(TAG, "Notifying " + mServiceAvailableCallbacks.size() + " callbacks that services are available");
                for (Runnable callback : mServiceAvailableCallbacks) {
                    try {
                        callback.run();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error in service available callback", e);
                    }
                }
                mServiceAvailableCallbacks.clear();
            }
        }
    }

    public IBinder getService(String name) {
        if (!areServicesAvailable()) {
            Logger.w(TAG, "Services not available, skipping service request: " + name);
            return null;
        }

        return getServiceInternal(name);
    }

    private IBinder getServiceInternal(String name) {
        IBinder binder = mServices.get(name);
        if (binder != null && binder.isBinderAlive()) {
            return binder;
        }
        
        // Check if we're in the main process and trying to access services
        // If so, we need to ensure the server process is running first
        if (PrisonCore.get().isMainProcess() && !isRunning()) {
            Logger.w(TAG, "Main process trying to access service " + name + " but server process not running, starting it...");
            startUp();
            // Wait a bit for the process to start, but with timeout
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Add timeout for provider calls to prevent hanging
        long startTime = System.currentTimeMillis();
        long timeout = 3000; // 3 seconds timeout
        
        try {
            Bundle bundle = new Bundle();
            bundle.putString("_B_|_server_name_", name);
            
            // Try to call the provider with timeout
            Bundle vm = null;
            try {
                vm = ProviderCall.callSafely(ProxyManifest.getBindProvider(), "VM", null, bundle);
            } catch (Exception e) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Logger.w(TAG, "Provider call timeout for service: " + name);
                }
                throw e;
            }
            
            if (vm == null) {
                Logger.w(TAG, "Provider call returned null for service: " + name);
                return null;
            }
            
            binder = BundleCompat.getBinder(vm, "_B_|_server_");
            Logger.d(TAG, "getService: " + name + ", " + binder);
            if (binder != null) {
                mServices.put(name, binder);
            } else {
                Logger.w(TAG, "Failed to get binder for service: " + name);
            }
            return binder;
        } catch (Exception e) {
            Logger.e(TAG, "Error getting service: " + name + ": " + e.getMessage());
            return null;
        }
    }

        
    /**
     * Check if the server process is running
     */
    private boolean isRunning() {
        try {
            // Try to access the SystemCallProvider to see if it's available
            Bundle testBundle = new Bundle();
            testBundle.putString("_B_|_server_name_", "test");
            
            // Use a more robust provider check with better error handling
            try {
                Bundle result = ProviderCall.callSafely(ProxyManifest.getBindProvider(), "VM", null, testBundle);
                if (result != null) {
                    Logger.d(TAG, "Server process is running - SystemCallProvider accessible");
                    return true;
                }
            } catch (Exception e) {
                Logger.w(TAG, "Provider call failed: " + e.getMessage());
            }
            
            // Fallback: Check if the provider authority exists
            try {
                String authority = ProxyManifest.getBindProvider();
                if (authority != null && !authority.isEmpty()) {
                    // Try to resolve the provider
                    android.content.pm.ProviderInfo providerInfo = mContext.getPackageManager()
                        .resolveContentProvider(authority, 0);
                    if (providerInfo != null) {
                        Logger.d(TAG, "Provider exists but call failed - server process may be starting");
                        return false; // Provider exists but not accessible yet
                    }
                }
            } catch (Exception e) {
                Logger.w(TAG, "Provider resolution failed: " + e.getMessage());
            }
            
            // Additional fallback: Check if DaemonService is running
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    for (android.app.ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
                        if (service.service.getClassName().contains("DaemonService")) {
                            Logger.d(TAG, "DaemonService is running - server process active");
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.w(TAG, "Service check failed: " + e.getMessage());
            }
            
            Logger.d(TAG, "Server process is not running");
            return false;
            
        } catch (Exception e) {
            Logger.w(TAG, "Error checking server process status: " + e.getMessage());
            return false;
        }
    }

    public boolean areServicesAvailable() {
        if (mServicesInitialized) {
            return true;
        }

        try {
            if (PrisonCore.get().isMainProcess() && !isRunning()) {
                Logger.w(TAG, "Server process not running, starting it...");
                startUp();
            }
            return initializeServices();
        } catch (Exception e) {
            Logger.e(TAG, "Failed to initialize services: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Schedule a delayed service start for later
     */
    private void scheduleDelayedServiceStart() {
        try {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Logger.d(TAG, "Executing delayed service start");
                    if (PrisonCore.get().isMainProcess() && !isRunning()) {
                        startUp();
                    }
                }
            }, 5000);
            Logger.d(TAG, "Scheduled delayed service start in 5 seconds");
        } catch (Exception e) {
            Logger.w(TAG, "Failed to schedule delayed service start: " + e.getMessage());
        }
    }

    /**
     * Ensure the server process is properly initialized
     */
    public void ensureServerInitialized() {
        if (PrisonCore.get().isMainProcess() && !isRunning()) {
            Logger.w(TAG, "Ensuring server process is initialized...");
            startUp();

            // Wait for the process to be ready
            int maxRetries = 5;
            int retryCount = 0;
            while (retryCount < maxRetries && !isRunning()) {
                try {
                    Thread.sleep(500);
                    retryCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (isRunning()) {
                Logger.d(TAG, "Server process initialized successfully");
            } else {
                Logger.w(TAG, "Server process failed to initialize, using fallback services");
            }
        }
    }
    /**
     * Start the server process if it's not running
     */
    private void startUp() {
        try {
            Logger.d(TAG, "Starting server process...");

            if (!isValidProcessState()) {
                Logger.w(TAG, "Process state is invalid, delaying service start");
                scheduleDelayedServiceStart();
                return;
            }

            Intent intent = new Intent();
            intent.setClass(mContext, DaemonService.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            boolean serviceStarted = false;
            int maxRetries = 3;

            for (int retry = 0; retry < maxRetries && !serviceStarted; retry++) {
                try {
                    if (retry > 0) {
                        Logger.d(TAG, "Retry attempt " + (retry + 1) + " for starting DaemonService");
                        if (retry == 1) {
                            Logger.d(TAG, "First retry - continuing immediately");
                        } else {
                            scheduleDelayedRetry(intent, retry);
                            return;
                        }
                    }

                    if (BuildCompat.isOreo()) {
                        mContext.startForegroundService(intent);
                        Logger.d(TAG, "Started DaemonService as foreground service");
                        serviceStarted = true;
                    } else {
                        mContext.startService(intent);
                        Logger.d(TAG, "Started DaemonService as regular service");
                        serviceStarted = true;
                    }

                } catch (SecurityException e) {
                    if (e.getMessage() != null && e.getMessage().contains("MissingForegroundServiceTypeException")) {
                        Logger.w(TAG, "Foreground service type missing, falling back to regular service");
                        try {
                            mContext.startService(intent);
                            Logger.d(TAG, "Started DaemonService as regular service (fallback)");
                            serviceStarted = true;
                    } catch (Exception fallbackEx) {
                        Logger.e(TAG, "Failed to start DaemonService even as regular service: " + fallbackEx.getMessage(), fallbackEx);
                        handleServiceStartFailure(retry, maxRetries, fallbackEx);
                    }
                    } else if (e.getMessage() != null && e.getMessage().contains("process is bad")) {
                        Logger.w(TAG, "Process is bad, attempting to recover and retry");
                        handleProcessBadError(retry, maxRetries);
                    } else {
                        Logger.e(TAG, "Security exception starting DaemonService: " + e.getMessage(), e);
                        handleServiceStartFailure(retry, maxRetries, e);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to start DaemonService: " + e.getMessage(), e);
                    handleServiceStartFailure(retry, maxRetries, e);
                }
            }

            if (!serviceStarted) {
                Logger.e(TAG, "Failed to start DaemonService after " + maxRetries + " attempts");
                tryAlternativeStartupMethods();
                return;
            }

            Logger.d(TAG, "Started DaemonService to initialize server process");
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start server process", e);
            scheduleDelayedServiceStart();
        }
    }

    /**
     * Check if the current process state is valid for starting services
     */
    private boolean isValidProcessState() {
        try {
            if (mContext == null) {
                Logger.w(TAG, "Context is null, process state invalid");
                return false;
            }
            if (!PrisonCore.get().isMainProcess()) {
                Logger.w(TAG, "Not in main process, skipping service start");
                return false;
            }
            try {
                mContext.getPackageName();
            } catch (Exception e) {
                Logger.w(TAG, "Package name access failed, process state invalid: " + e.getMessage());
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.w(TAG, "Process state validation failed: " + e.getMessage());
            return false;
        }
    }



    /**
     * Schedule a delayed retry for service startup
     */
    private void scheduleDelayedRetry(Intent intent, int retry) {
        try {
            int delayMs = 1000 * retry;
            Logger.d(TAG, "Scheduling delayed retry in " + delayMs + "ms");

            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Logger.d(TAG, "Executing delayed retry for DaemonService");
                        if (BuildCompat.isOreo()) {
                            mContext.startForegroundService(intent);
                        } else {
                            mContext.startService(intent);
                        }
                        Logger.d(TAG, "Delayed retry successful");
                    } catch (Exception e) {
                        Logger.e(TAG, "Delayed retry failed: " + e.getMessage());
                        tryAlternativeStartupMethods();
                    }
                }
            }, delayMs);

        } catch (Exception e) {
            Logger.w(TAG, "Failed to schedule delayed retry: " + e.getMessage());
            tryAlternativeStartupMethods();
        }
    }


    /**
     * Handle service startup failures with appropriate recovery actions
     */
    private void handleServiceStartFailure(int retry, int maxRetries, Exception e) {
        if (retry < maxRetries - 1) {
            Logger.w(TAG, "Service start failed, will retry. Attempt " + (retry + 1) + " of " + maxRetries);
        } else {
            Logger.e(TAG, "Service start failed after " + maxRetries + " attempts: " + e.getMessage());
            tryAlternativeStartupMethods();
        }
    }

    /**
     * Handle "process is bad" errors with recovery strategies
     */
    private void handleProcessBadError(int retry, int maxRetries) {
        if (retry < maxRetries - 1) {
            Logger.w(TAG, "Process is bad, attempting recovery. Attempt " + (retry + 1) + " of " + maxRetries);

            try {
                scheduleProcessRecovery(retry, maxRetries);
            } catch (Exception e) {
                Logger.w(TAG, "Process recovery failed: " + e.getMessage());
            }
        } else {
            Logger.e(TAG, "Process recovery failed after " + maxRetries + " attempts");
            tryAlternativeStartupMethods();
        }
    }

    /**
     * Schedule process recovery asynchronously
     */
    private void scheduleProcessRecovery(int retry, int maxRetries) {
        try {
            int delayMs = 2000;
            Logger.d(TAG, "Scheduling process recovery in " + delayMs + "ms");
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (PrisonCore.get().isMainProcess() && !isRunning()) {
                            startUp();
                        }
                    } catch (Exception e) {
                        Logger.w(TAG, "Process recovery execution failed: " + e.getMessage());
                    }
                }
            }, delayMs);
        } catch (Exception e) {
            Logger.w(TAG, "Failed to schedule process recovery: " + e.getMessage());
        }
    }

    /**
     * Try alternative methods to start the service
     */
    private void tryAlternativeStartupMethods() {
        Logger.w(TAG, "Trying alternative startup methods...");
        try {
            Context appContext = mContext.getApplicationContext();
            if (appContext != null && appContext != mContext) {
                Intent intent = new Intent();
                intent.setClass(appContext, DaemonService.class);
                appContext.startService(intent);
                Logger.d(TAG, "Application context startup successful");
            }
        } catch (Exception e) {
            Logger.w(TAG, "Alternative startup failed: " + e.getMessage());
        }
    }


    /**
     * Start DaemonService in server process with retry logic and error handling
     */
    public void startDaemon() {
        try {
            // Check if we're in a valid state to start services
            if (!isValidProcessState()) {
                Logger.w(TAG, "Server process state is invalid, delaying service start");
                // Schedule a delayed retry for server process
                scheduleDelayedServerServiceStart();
                return;
            }

            Intent intent = new Intent();
            intent.setClass(mContext, DaemonService.class);

            // Add flags to help with process startup
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            boolean serviceStarted = false;
            int maxRetries = 3;

            for (int retry = 0; retry < maxRetries && !serviceStarted; retry++) {
                try {
                    if (retry > 0) {
                        Logger.d(TAG, "Retry attempt " + (retry + 1) + " for starting DaemonService in server process");
                        // Use non-blocking delay instead of Thread.sleep
                        if (retry == 1) {
                            // For first retry, just continue immediately
                            Logger.d(TAG, "First retry for server process - continuing immediately");
                        } else {
                            // For subsequent retries, schedule delayed execution
                            scheduleDelayedServerRetry(intent, retry);
                            return;
                        }
                    }

                    if (BuildCompat.isOreo()) {
                        mContext.startForegroundService(intent);
                        Logger.d(TAG, "Started DaemonService as foreground service in server process");
                        serviceStarted = true;
                    } else {
                        mContext.startService(intent);
                        Logger.d(TAG, "Started DaemonService as regular service in server process");
                        serviceStarted = true;
                    }

                } catch (SecurityException e) {
                    if (e.getMessage() != null && e.getMessage().contains("MissingForegroundServiceTypeException")) {
                        Logger.w(TAG, "Foreground service type missing in server process, falling back to regular service");
                        try {
                            mContext.startService(intent);
                            Logger.d(TAG, "Started DaemonService as regular service in server process (fallback)");
                            serviceStarted = true;
                        } catch (Exception fallbackEx) {
                            Logger.e(TAG, "Failed to start DaemonService in server process even as regular service: " + fallbackEx.getMessage(), fallbackEx);
                            handleServerServiceStartFailure(retry, maxRetries, fallbackEx);
                        }
                    } else if (e.getMessage() != null && e.getMessage().contains("process is bad")) {
                        Logger.w(TAG, "Server process is bad, attempting to recover and retry");
                        handleServerProcessBadError(retry, maxRetries);
                    } else {
                        Logger.e(TAG, "Security exception starting DaemonService in server process: " + e.getMessage(), e);
                        handleServerServiceStartFailure(retry, maxRetries, e);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to start DaemonService in server process: " + e.getMessage(), e);
                    handleServerServiceStartFailure(retry, maxRetries, e);
                }
            }

            if (!serviceStarted) {
                Logger.e(TAG, "Failed to start DaemonService in server process after " + maxRetries + " attempts");
                // Try alternative startup methods for server process
                tryAlternativeServerStartupMethods();
            }

        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error starting DaemonService in server process: " + e.getMessage(), e);
            // Schedule a delayed retry
            scheduleDelayedServerServiceStart();
        }
    }

    /**
     * Schedule a delayed service start for server process
     */
    private void scheduleDelayedServerServiceStart() {
        try {
            // Schedule a delayed retry using a handler
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Logger.d(TAG, "Executing delayed server service start");
                    if (PrisonCore.get().isServerProcess() && PrisonCore.get().getSettings().isEnableDaemonService()) {
                        // Re-trigger the server process service start
                        try {
                            Intent intent = new Intent();
                            intent.setClass(mContext, DaemonService.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            if (BuildCompat.isOreo()) {
                                mContext.startForegroundService(intent);
                            } else {
                                mContext.startService(intent);
                            }
                            Logger.d(TAG, "Delayed server service start successful");
                        } catch (Exception e) {
                            Logger.e(TAG, "Delayed server service start failed: " + e.getMessage());
                        }
                    }
                }
            }, 5000); // 5 second delay

            Logger.d(TAG, "Scheduled delayed server service start in 5 seconds");
        } catch (Exception e) {
            Logger.w(TAG, "Failed to schedule delayed server service start: " + e.getMessage());
        }
    }

    /**
     * Schedule a delayed retry for server process service startup
     */
    private void scheduleDelayedServerRetry(Intent intent, int retry) {
        try {
            int delayMs = 1000 * retry;
            Logger.d(TAG, "Scheduling delayed server retry in " + delayMs + "ms");

            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Logger.d(TAG, "Executing delayed server retry for DaemonService");
                        if (BuildCompat.isOreo()) {
                            mContext.startForegroundService(intent);
                        } else {
                            mContext.startService(intent);
                        }
                        Logger.d(TAG, "Delayed server retry successful");
                    } catch (Exception e) {
                        Logger.e(TAG, "Delayed server retry failed: " + e.getMessage());
                        // Try alternative methods
                        tryAlternativeServerStartupMethods();
                    }
                }
            }, delayMs);

        } catch (Exception e) {
            Logger.w(TAG, "Failed to schedule delayed server retry: " + e.getMessage());
            // Fall back to alternative methods immediately
            tryAlternativeServerStartupMethods();
        }
    }
    /**
     * Try alternative methods to start the service in server process
     */
    private void tryAlternativeServerStartupMethods() {
        Logger.w(TAG, "Trying alternative startup methods for server process...");
        try {
            Context appContext = mContext.getApplicationContext();
            if (appContext != null && appContext != mContext) {
                Intent intent = new Intent();
                intent.setClass(appContext, DaemonService.class);
                appContext.startService(intent);
                Logger.d(TAG, "Application context startup successful for server process");
            }
        } catch (Exception e) {
            Logger.w(TAG, "Alternative startup failed for server process: " + e.getMessage());
        }
    }



    /**
     * Handle server service startup failures with appropriate recovery actions
     */
    private void handleServerServiceStartFailure(int retry, int maxRetries, Exception e) {
        if (retry < maxRetries - 1) {
            Logger.w(TAG, "Server service start failed, will retry. Attempt " + (retry + 1) + " of " + maxRetries);
        } else {
            Logger.e(TAG, "Server service start failed after " + maxRetries + " attempts: " + e.getMessage());
            // Try alternative startup methods for server process
            tryAlternativeServerStartupMethods();
        }
    }

    /**
     * Handle "process is bad" errors for server process with recovery strategies
     */
    private void handleServerProcessBadError(int retry, int maxRetries) {
        if (retry < maxRetries - 1) {
            Logger.w(TAG, "Server process is bad, attempting recovery. Attempt " + (retry + 1) + " of " + maxRetries);

            try {
                scheduleServerProcessRecovery(retry, maxRetries);
            } catch (Exception e) {
                Logger.w(TAG, "Server process recovery failed: " + e.getMessage());
            }
        } else {
            Logger.e(TAG, "Server process recovery failed after " + maxRetries + " attempts");
            tryAlternativeServerStartupMethods();
        }
    }

    /**
     * Schedule server process recovery asynchronously
     */
    private void scheduleServerProcessRecovery(int retry, int maxRetries) {
        try {
            int delayMs = 2000;
            Logger.d(TAG, "Scheduling server process recovery in " + delayMs + "ms");
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (PrisonCore.get().isServerProcess() && PrisonCore.get().getSettings().isEnableDaemonService()) {
                            Intent intent = new Intent();
                            intent.setClass(mContext, DaemonService.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            if (BuildCompat.isOreo()) {
                                mContext.startForegroundService(intent);
                            } else {
                                mContext.startService(intent);
                            }
                        }
                    } catch (Exception e) {
                        Logger.w(TAG, "Server process recovery execution failed: " + e.getMessage());
                    }
                }
            }, delayMs);
        } catch (Exception e) {
            Logger.w(TAG, "Failed to schedule server process recovery: " + e.getMessage());
        }
    }

    /**
     * Warm up all required services to ensure they are ready
     */
    public void warmupServices() {
        for (String serviceName : REQUIRED_SERVICES) {
            try {
                getService(serviceName);
            } catch (Exception e) {
                Logger.w(TAG, "Failed to warm up service: " + serviceName, e);
            }
        }
    }
}