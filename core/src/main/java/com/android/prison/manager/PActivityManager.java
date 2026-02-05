package com.android.prison.manager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.prison.base.LauncherActivity;
import com.android.prison.base.PActivityThread;
import com.android.prison.core.PrisonCore;
import com.android.prison.system.ServiceManager;
import com.android.prison.system.am.IPActivityManagerService;
import com.android.prison.entity.AppConfig;
import com.android.prison.entity.UnbindRecord;
import com.android.prison.entity.PendingResultData;
import com.android.prison.entity.RunningAppProcessInfo;
import com.android.prison.entity.RunningServiceInfo;
import com.android.prison.utils.Logger;

public class PActivityManager extends Manager<IPActivityManagerService> {
    private static final String TAG = PActivityManager.class.getSimpleName();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;
    private static final long PROGRESSIVE_DELAY_MS = 200;
    
    private final Context mContext;
    private static final PActivityManager sActivityManager = new PActivityManager();

    private PActivityManager() {
        mContext = PrisonCore.getContext();
    }

    public static PActivityManager get() {
        return sActivityManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.ACTIVITY_MANAGER;
    }

    /**
     * Execute a service call with retry logic
     */
    private <T> T executeWithRetry(ServiceCallable<T> callable, String operationName, T defaultValue) {
        int retryCount = 0;
        
        while (retryCount < MAX_RETRIES) {
            try {
                IPActivityManagerService service = getService();
                if (service != null) {
                    T result = callable.call(service);
                    if (result != null || !callable.isNullable()) {
                        return result;
                    }
                    Logger.w(TAG, operationName + " returned null, retry " + (retryCount + 1) + "/" + MAX_RETRIES);
                } else {
                    Logger.w(TAG, "ActivityManager service is null for " + operationName + ", retry " + (retryCount + 1) + "/" + MAX_RETRIES);
                }
            } catch (DeadObjectException e) {
                Logger.w(TAG, "ActivityManager service died during " + operationName + ", clearing cache and retrying " + (retryCount + 1) + "/" + MAX_RETRIES, e);
                clearServiceCache();
                if (!sleep(RETRY_DELAY_MS)) {
                    break;
                }
            } catch (RemoteException e) {
                Logger.e(TAG, "RemoteException in " + operationName, e);
                break;
            } catch (Exception e) {
                Logger.e(TAG, "Unexpected error in " + operationName, e);
                break;
            }
            
            // Progressive delay for null service case
            if (retryCount < MAX_RETRIES - 1) {
                sleep(PROGRESSIVE_DELAY_MS * (retryCount + 1));
            }
            retryCount++;
        }
        
        Logger.e(TAG, "Failed to execute " + operationName + " after " + MAX_RETRIES + " retries");
        return defaultValue;
    }

    /**
     * Execute a void service call with retry logic
     */
    private void executeWithRetryVoid(ServiceRunnable runnable, String operationName) {
        executeWithRetry(service -> {
            runnable.run(service);
            return Boolean.TRUE;
        }, operationName, Boolean.FALSE);
    }

    /**
     * Execute a simple service call without retry
     */
    private <T> T executeSimple(ServiceCallable<T> callable, String operationName, T defaultValue) {
        try {
            IPActivityManagerService service = getService();
            if (service != null) {
                return callable.call(service);
            }
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in " + operationName, e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in " + operationName, e);
        }
        return defaultValue;
    }

    /**
     * Sleep helper with interrupt handling
     */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    private interface ServiceCallable<T> {
        T call(IPActivityManagerService service) throws RemoteException;
        default boolean isNullable() {
            return true;
        }
    }

    @FunctionalInterface
    private interface ServiceRunnable {
        void run(IPActivityManagerService service) throws RemoteException;
    }

    public AppConfig initProcess(String packageName, String processName, int userId) {
        return executeWithRetry(
            service -> service.initProcess(packageName, processName, userId),
            "initProcess(" + packageName + ", " + processName + ")",
            null
        );
    }

    public void restartProcess(String packageName, String processName, int userId) {
        executeWithRetryVoid(
            service -> service.restartProcess(packageName, processName, userId),
            "restartProcess(" + packageName + ", " + processName + ")"
        );
    }

    public void launch(Intent intent, int userId) {
        try {
            Intent splash = new Intent();
            splash.setClass(mContext, LauncherActivity.class);
            splash.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            splash.putExtra(LauncherActivity.KEY_INTENT, intent);
            splash.putExtra(LauncherActivity.KEY_PKG, intent.getPackage());
            splash.putExtra(LauncherActivity.KEY_USER_ID, userId);
            mContext.startActivity(splash);
            Logger.d(TAG, "LauncherActivity.launch() called for package: " + intent.getPackage());
        } catch (Exception e) {
            Logger.e(TAG, "Error in launch()", e);
        }
    }

    public void startActivity(Intent intent, int userId) {
        executeWithRetryVoid(
            service -> service.startActivity(intent, userId),
            "startActivity"
        );
    }

    public int startActivityAms(int userId, Intent intent, String resolvedType, IBinder resultTo, 
                                String resultWho, int requestCode, int flags, Bundle options) {
        return executeWithRetry(
            service -> service.startActivityAms(userId, intent, resolvedType, resultTo, resultWho, requestCode, flags, options),
            "startActivityAms",
            -1
        );
    }

    public int startActivities(int userId, Intent[] intent, String[] resolvedType, IBinder resultTo, Bundle options) {
        return executeSimple(
            service -> service.startActivities(userId, intent, resolvedType, resultTo, options),
            "startActivities",
            -1
        );
    }

    public ComponentName startService(Intent intent, String resolvedType, boolean requireForeground, int userId) {
        return executeWithRetry(
            service -> service.startService(intent, resolvedType, requireForeground, userId),
            "startService",
            null
        );
    }

    public int stopService(Intent intent, String resolvedType, int userId) {
        return executeSimple(
            service -> service.stopService(intent, resolvedType, userId),
            "stopService",
            -1
        );
    }

    public Intent bindService(Intent service, IBinder binder, String resolvedType, int userId) {
        return executeSimple(
            serviceManager -> serviceManager.bindService(service, binder, resolvedType, userId),
            "bindService",
            null
        );
    }

    public void unbindService(IBinder binder, int userId) {
        executeSimple(
            service -> {
                service.unbindService(binder, userId);
                return null;
            },
            "unbindService",
            null
        );
    }

    public void stopServiceToken(ComponentName componentName, IBinder token, int userId) {
        executeSimple(
            service -> {
                service.stopServiceToken(componentName, token, userId);
                return null;
            },
            "stopServiceToken",
            null
        );
    }

    public void onStartCommand(Intent proxyIntent, int userId) {
        executeSimple(
            service -> {
                service.onStartCommand(proxyIntent, userId);
                return null;
            },
            "onStartCommand",
            null
        );
    }

    public UnbindRecord onServiceUnbind(Intent proxyIntent, int userId) {
        return executeSimple(
            service -> service.onServiceUnbind(proxyIntent, userId),
            "onServiceUnbind",
            null
        );
    }

    public void onServiceDestroy(Intent proxyIntent, int userId) {
        executeSimple(
            service -> {
                service.onServiceDestroy(proxyIntent, userId);
                return null;
            },
            "onServiceDestroy",
            null
        );
    }

    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) {
        try {
            IPActivityManagerService service = getService();
            if (service != null) {
                return service.acquireContentProviderClient(providerInfo);
            } else {
                Logger.w(TAG, "ActivityManager service is null for acquireContentProviderClient");
            }
        } catch (DeadObjectException e) {
            Logger.w(TAG, "ActivityManager service died during acquireContentProviderClient, clearing cache", e);
            clearServiceCache();
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in acquireContentProviderClient", e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in acquireContentProviderClient", e);
        }
        return null;
    }

    public Intent sendBroadcast(Intent intent, String resolvedType, int userId) {
        return executeSimple(
            service -> service.sendBroadcast(intent, resolvedType, userId),
            "sendBroadcast",
            null
        );
    }

    public IBinder peekService(Intent intent, String resolvedType, int userId) {
        return executeSimple(
            service -> service.peekService(intent, resolvedType, userId),
            "peekService",
            null
        );
    }

    public void onActivityCreated(int taskId, IBinder token, IBinder activityRecord) {
        executeSimple(
            service -> {
                service.onActivityCreated(taskId, token, activityRecord);
                return null;
            },
            "onActivityCreated",
            null
        );
    }

    public void onActivityResumed(IBinder token) {
        executeSimple(
            service -> {
                service.onActivityResumed(token);
                return null;
            },
            "onActivityResumed",
            null
        );
    }

    public void onActivityDestroyed(IBinder token) {
        executeSimple(
            service -> {
                service.onActivityDestroyed(token);
                return null;
            },
            "onActivityDestroyed",
            null
        );
    }

    public void onFinishActivity(IBinder token) {
        executeSimple(
            service -> {
                service.onFinishActivity(token);
                return null;
            },
            "onFinishActivity",
            null
        );
    }

    public RunningAppProcessInfo getRunningAppProcesses(String callerPackage, int userId) {
        try {
            return getService().getRunningAppProcesses(callerPackage, userId);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getRunningAppProcesses", e);
        }
        return null;
    }

    public RunningServiceInfo getRunningServices(String callerPackage, int userId) {
        try {
            return getService().getRunningServices(callerPackage, userId);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getRunningServices", e);
        }
        return null;
    }

    public void scheduleBroadcastReceiver(Intent intent, PendingResultData pendingResultData, int userId) {
        try {
            getService().scheduleBroadcastReceiver(intent, pendingResultData, userId);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in scheduleBroadcastReceiver", e);
        }
    }

    public void finishBroadcast(PendingResultData data) {
        try {
            getService().finishBroadcast(data);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in finishBroadcast", e);
        }
    }

    public String getCallingPackage(IBinder token, int userId) {
        return executeSimple(
            service -> service.getCallingPackage(token, userId),
            "getCallingPackage",
            null
        );
    }

    public ComponentName getCallingActivity(IBinder token, int userId) {
        return executeSimple(
            service -> service.getCallingActivity(token, userId),
            "getCallingActivity",
            null
        );
    }

    public void getIntentSender(IBinder target, String packageName, int uid) {
        try {
            getService().getIntentSender(target, packageName, uid, PActivityThread.getUserId());
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getIntentSender", e);
        }
    }

    public String getPackageForIntentSender(IBinder target) {
        return executeSimple(
            service -> service.getPackageForIntentSender(target, PActivityThread.getUserId()),
            "getPackageForIntentSender",
            null
        );
    }

    public int getUidForIntentSender(IBinder target) {
        return executeSimple(
            service -> service.getUidForIntentSender(target, PActivityThread.getUserId()),
            "getUidForIntentSender",
            -1
        );
    }
}
