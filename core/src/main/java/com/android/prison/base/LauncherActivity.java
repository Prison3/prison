package com.android.prison.base;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.prison.core.PrisonCore;
import com.android.prison.manager.PActivityManager;
import com.android.prison.manager.PPackageManager;
import com.android.prison.R;

import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;
import com.android.prison.utils.Logger;

/**
 * LauncherActivity - Handles the launch of virtual apps
 * This activity serves as a bridge between the host app and virtual apps
 */
public class LauncherActivity extends Activity {
    public static final String TAG = LauncherActivity.class.getSimpleName();

    public static final String KEY_INTENT = "launch_intent";
    public static final String KEY_PKG = "launch_pkg";
    public static final String KEY_USER_ID = "launch_user_id";
    private boolean isRunning = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            
            Intent intent = getIntent();
            if (intent == null) {
                Logger.w(TAG, "Intent is null, finishing activity");
                finish();
                return;
            }
            
            Intent launchIntent = intent.getParcelableExtra(KEY_INTENT);
            String packageName = intent.getStringExtra(KEY_PKG);
            int userId = intent.getIntExtra(KEY_USER_ID, 0);

            if (launchIntent == null || packageName == null) {
                Logger.w(TAG, "Missing launch intent or package name, finishing activity");
                finish();
                return;
            }

            Logger.d(TAG, "LauncherActivity.onCreate() for package: " + packageName + ", userId: " + userId);

            // Get package info with enhanced error handling
            PackageInfo packageInfo = getPackageInfoWithFallback(packageName, userId);
            
            if (packageInfo == null) {
                Logger.w(TAG, "Package info not available for " + packageName + ", but proceeding with launch");
                // Don't fail immediately - try to proceed anyway
            } else {
                Logger.d(TAG, "Successfully retrieved package info for " + packageName);
            }
            
            // Properly load the app icon and app name
            Drawable drawable = null;
            String appName = packageName;
            try {
                if (packageInfo != null && packageInfo.applicationInfo != null) {
                    PackageManager pm = getPackageManager();
                    drawable = pm.getApplicationIcon(packageInfo.applicationInfo);
                    CharSequence label = pm.getApplicationLabel(packageInfo.applicationInfo);
                    if (label != null) appName = label.toString();
                }
            } catch (Exception e) {
                Logger.w(TAG, "Failed to load app icon or name for " + packageName + ": " + e.getMessage());
            }
            setContentView(R.layout.activity_launcher);
            ImageView iconView = findViewById(R.id.iv_icon);
            TextView nameView = findViewById(R.id.tv_app_name);
            if (nameView != null) {
                nameView.setText(appName);
                nameView.setAlpha(0f);
                nameView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(200)
                    .start();
            }
            if (iconView != null && drawable != null) {
                iconView.setImageDrawable(drawable);
                iconView.setScaleX(0.7f);
                iconView.setScaleY(0.7f);
                iconView.setAlpha(0f);
                iconView.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator())
                    .withEndAction(() -> iconView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start())
                    .start();
            }
            
            // Launch the app in a separate thread to avoid blocking the UI
            launchAppAsync(launchIntent, userId);
            
        } catch (Exception e) {
            Logger.e(TAG, "Critical error in LauncherActivity.onCreate()", e);
            finish();
        }
    }

    /**
     * Get package info with enhanced error handling and fallback mechanisms
     */
    private PackageInfo getPackageInfoWithFallback(String packageName, int userId) {
        try {
            // First attempt: Try to get package info normally
            return PPackageManager.get().getPackageInfo(packageName, 0, userId);
        } catch (Exception e) {
            Logger.w(TAG, "Failed to get package info for " + packageName + " (attempt 1): " + e.getMessage());
            
            try {
                // Second attempt: Try with different flags
                return PPackageManager.get().getPackageInfo(packageName,
                    android.content.pm.PackageManager.GET_META_DATA, userId);
            } catch (Exception e2) {
                Logger.w(TAG, "Failed to get package info for " + packageName + " (attempt 2): " + e2.getMessage());
                
                try {
                    // Third attempt: Try to get application info instead
                    android.content.pm.ApplicationInfo appInfo = PPackageManager.get()
                        .getApplicationInfo(packageName, 0, userId);
                    
                    if (appInfo != null) {
                        // Create a minimal PackageInfo from ApplicationInfo
                        PackageInfo fallbackInfo = new PackageInfo();
                        fallbackInfo.packageName = packageName;
                        fallbackInfo.applicationInfo = appInfo;
                        fallbackInfo.versionCode = 1;
                        fallbackInfo.versionName = "1.0";
                        fallbackInfo.firstInstallTime = System.currentTimeMillis();
                        fallbackInfo.lastUpdateTime = System.currentTimeMillis();
                        
                        Logger.d(TAG, "Created fallback PackageInfo for " + packageName);
                        return fallbackInfo;
                    }
                } catch (Exception e3) {
                    Logger.w(TAG, "Failed to get application info for " + packageName + ": " + e3.getMessage());
                }
            }
        }
        
        return null;
    }

    /**
     * Launch the app asynchronously to avoid blocking the UI thread
     */
    private void launchAppAsync(final Intent launchIntent, final int userId) {
        new Thread(() -> {
            try {
                Logger.d(TAG, "Starting app launch in background thread");
                
                // Add a small delay to ensure the launcher activity is properly displayed
                Thread.sleep(100);
                
                // Launch the app
                PActivityManager.get().startActivity(launchIntent, userId);
                
                Logger.d(TAG, "App launch initiated successfully");
            } catch (Exception e) {
                Logger.e(TAG, "Error launching app", e);
                
                // Try to show an error message to the user
                runOnUiThread(() -> {
                    try {
                        // You could show a toast or dialog here
                        Logger.e(TAG, "Failed to launch app: " + e.getMessage());
                    } catch (Exception uiException) {
                        Logger.e(TAG, "Error showing error message", uiException);
                    }
                });
            }
        }, "AppLaunchThread").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRunning) {
            finish();
        }
    }
}
