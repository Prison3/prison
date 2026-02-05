package com.android.prison.base;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import com.android.prison.R;
import com.android.prison.core.PrisonCore;
import com.android.prison.utils.Logger;

import java.util.List;

/**
 * Utility class for determining the type of the current process.
 */
public class ProcessType {
    private static final String TAG = ProcessType.class.getSimpleName();

    /**
     * Process type enumeration
     */
    public enum Type {
        /**
         * Server process
         */
        Server,
        /**
         * Black app process (virtualized app process)
         */
        Prison,
        /**
         * Main process
         */
        Main,
    }

    /**
     * Get the current process name.
     * 
     * @param context The application context
     * @return The process name
     * @throws RuntimeException If the process name cannot be determined
     */
    private static String getProcessName(Context context) {
        int pid = Process.myPid();
        String processName = null;

        // Try modern approach first (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.pid == pid) {
                            processName = info.processName;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.w(TAG, "Failed to get process name using modern API", e);
            }
        }

        // Fallback to deprecated method if modern approach fails
        if (processName == null) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.pid == pid) {
                            processName = info.processName;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.w(TAG, "Failed to get process name using deprecated API", e);
            }
        }

        if (processName == null) {
            throw new RuntimeException("processName = null");
        }
        return processName;
    }

    /**
     * Determine the type of the current process.
     * 
     * @param context The application context
     * @return The process type
     */
    public static Type determineProcessType(Context context) {
        String processName = getProcessName(context);
        if (processName.equals(PrisonCore.getPackageName())) {
            return Type.Main;
        } else if (processName.endsWith(context.getString(R.string.server_process))) {
            return Type.Server;
        } else {
            return Type.Prison;
        }
    }
}
