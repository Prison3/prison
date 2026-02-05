package com.android.prison.base;

import android.content.pm.ApplicationInfo;

import com.android.prison.interfaces.android.ddm.BRDdmHandleAppName;
import com.android.prison.interfaces.android.os.BRProcess;

public interface IInjector {
    void inject();

    boolean isBadEnv();

    class VirtualRuntime {

        private static String sInitialPackageName;
        private static String sProcessName;

        public static String getProcessName() {
            return sProcessName;
        }

        public static String getInitialPackageName() {
            return sInitialPackageName;
        }

        public static void setupRuntime(String processName, ApplicationInfo appInfo) {
            if (sProcessName != null) {
                return;
            }
            sInitialPackageName = appInfo.packageName;
            sProcessName = processName;
            BRProcess.get().setArgV0(processName);
            BRDdmHandleAppName.get().setAppName(processName, 0);
        }
    }
}
