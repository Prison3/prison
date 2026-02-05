package com.android.prison.utils;

import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.util.Locale;

import com.android.prison.core.PrisonCore;
import com.android.prison.base.PActivityThread;

public class TaskDescriptionCompat {
    public static ActivityManager.TaskDescription fix(ActivityManager.TaskDescription td) {
        String label = td.getLabel();
        Bitmap icon = td.getIcon();

        if (label != null && icon != null)
            return td;

        label = getTaskDescriptionLabel(PActivityThread.getUserId(), getApplicationLabel());
        // Skip icon loading to prevent resource loading errors
        // Drawable drawable = getApplicationIcon();
        // if (drawable == null)
        //     return td;

        // ActivityManager am = (ActivityManager) Prison.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        // int iconSize = am.getLauncherLargeIconSize();
        // icon = DrawableUtils.drawableToBitmap(drawable, iconSize, iconSize);
        td = new ActivityManager.TaskDescription(label, null, td.getPrimaryColor());
        return td;
    }

    public static String getTaskDescriptionLabel(int userId, CharSequence label) {
        return String.format(Locale.CHINA, "[B%d]%s", userId, label);
    }

    private static CharSequence getApplicationLabel() {
        try {
            PackageManager pm = PrisonCore.getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(PActivityThread.getAppPackageName(), 0));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Drawable getApplicationIcon() {
        try {
            // Skip icon loading to prevent resource loading errors
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }
}
