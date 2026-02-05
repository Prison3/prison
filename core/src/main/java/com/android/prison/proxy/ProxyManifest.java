package com.android.prison.proxy;

import java.util.Locale;

import com.android.prison.core.PrisonCore;

public class ProxyManifest {
    public static final int FREE_COUNT = 50;

    public static boolean isProxy(String msg) {
        return getBindProvider().equals(msg) || msg.contains("proxy_content_provider_");
    }

    public static String getBindProvider() {
        return PrisonCore.getPackageName() + ".prison.SystemCallProvider";
    }

    public static String getProxyAuthorities(int index) {
        return String.format(Locale.CHINA, "%s.proxy_content_provider_%d", PrisonCore.getPackageName(), index);
    }

    public static String getProxyPendingActivity(int index) {
        return String.format(Locale.CHINA, "com.android.prison.proxy.ProxyPendingActivity$P%d", index);
    }

    public static String getProxyActivity(int index) {
        return String.format(Locale.CHINA, "com.android.prison.proxy.ProxyActivity$P%d", index);
    }

    public static String TransparentProxyActivity(int index) {
        return String.format(Locale.CHINA, "com.android.prison.proxy.TransparentProxyActivity$P%d", index);
    }

    public static String getProxyService(int index) {
        return String.format(Locale.CHINA, "com.android.prison.proxy.ProxyService$P%d", index);
    }

    public static String getProxyJobService(int index) {
        return String.format(Locale.CHINA, "com.android.prison.proxy.ProxyJobService$P%d", index);
    }

    public static String getProxyFileProvider() {
        return PrisonCore.getPackageName() + ".prison.FileProvider";
    }

    public static String getProxyReceiver() {
        return PrisonCore.getPackageName() + ".stub_receiver";
    }

    public static String getProcessName(int bPid) {
        return PrisonCore.getPackageName() + ":p" + bPid;
    }
}
