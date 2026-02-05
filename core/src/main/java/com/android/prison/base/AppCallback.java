package com.android.prison.base;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

public interface AppCallback {
    public static AppCallback EMPTY = new AppCallback() {

        @Override
        public void beforeMainLaunchApk(String packageName, int userid) {

        }

        @Override
        public boolean onStoragePermissionNeeded(String packageName, int userId) {
            return false;
        }

        @Override
        public void beforeMainActivityOnCreate(Activity activity) {

        }

        @Override
        public void afterMainActivityOnCreate(Activity activity) {

        }

        @Override
        public void beforeCreateApplication(String packageName, String processName, Context context, int userId) {

        }

        @Override
        public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {

        }

        @Override
        public void afterApplicationOnCreate(String packageName, String processName, Application application, int userId) {

        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {

        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }

        @Override
        public void onBeforeCreateApplication(String packageName, String processName, Context context, int userId) {

        }
    };

    public void beforeMainLaunchApk(String packageName, int userid);

    /**
     * Called when storage permission is needed before launching an app.
     * Override this in your host app to show permission request UI.
     *
     * @param packageName The package being launched
     * @param userId The user ID
     * @return true if the host app will handle the permission request (launch will be cancelled),
     *         false to continue launching anyway
     */
    public boolean onStoragePermissionNeeded(String packageName, int userId);

    public void beforeMainActivityOnCreate(Activity activity);

    public void afterMainActivityOnCreate(Activity activity);

    public void beforeCreateApplication(String packageName, String processName, Context context, int userId);

    public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId);

    public void afterApplicationOnCreate(String packageName, String processName, Application application, int userId);

    public void onActivityCreated(Activity activity, Bundle savedInstanceState) ;

    public void onActivityStarted(Activity activity) ;

    public void onActivityResumed(Activity activity) ;

    public void onActivityPaused(Activity activity) ;

    public void onActivityStopped(Activity activity) ;

    public void onActivitySaveInstanceState(Activity activity, Bundle outState) ;

    public void onActivityDestroyed(Activity activity);

    void onBeforeCreateApplication(String packageName, String processName, Context context, int userId);
}
