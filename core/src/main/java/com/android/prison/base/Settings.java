package com.android.prison.base;

import java.io.File;

public abstract class Settings {

    public abstract String getHostPackageName();

    public boolean isEnableDaemonService() {
        return true;
    }

    public boolean isEnableLauncherActivity() {
        return true;
    }

    /**
     * This method is called when an internal application requests to install a new application.
     *
     * @return Is it handled?
     */
    public boolean requestInstallPackage(File file, int userId) {
        return false;
    }

}
