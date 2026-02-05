package com.android.prison.utils;

import android.content.Context;
import android.content.ContextWrapper;

import com.android.prison.interfaces.android.app.BRContextImpl;
import com.android.prison.interfaces.android.app.BRContextImplKitkat;
import com.android.prison.interfaces.android.content.AttributionSourceStateContext;
import com.android.prison.interfaces.android.content.BRAttributionSource;
import com.android.prison.interfaces.android.content.BRAttributionSourceState;
import com.android.prison.interfaces.android.content.BRContentResolver;
import com.android.prison.core.PrisonCore;

public class ContextCompat {
    public static final String TAG = ContextCompat.class.getSimpleName();

    public static void fixAttributionSourceState(Object obj, int uid) {
        Object mAttributionSourceState;
        if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
            mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();

            AttributionSourceStateContext attributionSourceStateContext = BRAttributionSourceState.get(mAttributionSourceState);
            attributionSourceStateContext._set_packageName(PrisonCore.getPackageName());
            attributionSourceStateContext._set_uid(uid);
            fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid);
        }
    }

    public static void fix(Context context) {
        try {
            // Check if context is null
            if (context == null) {
                Logger.w(TAG, "Context is null, skipping ContextCompat.fix");
                return;
            }
            
            int deep = 0;
            while (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
                deep++;
                if (deep >= 10) {
                    return;
                }
            }
            
            // Check if context is still null after unwrapping
            if (context == null) {
                Logger.w(TAG, "Base context is null after unwrapping, skipping ContextCompat.fix");
                return;
            }
            
            BRContextImpl.get(context)._set_mPackageManager(null);
            try {
                context.getPackageManager();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            BRContextImpl.get(context)._set_mBasePackageName(PrisonCore.getPackageName());
            BRContextImplKitkat.get(context)._set_mOpPackageName(PrisonCore.getPackageName());
            
            try {
                BRContentResolver.get(context.getContentResolver())._set_mPackageName(PrisonCore.getPackageName());
            } catch (Exception e) {
                Logger.w(TAG, "Failed to fix content resolver: " + e.getMessage());
            }

            if (BuildCompat.isS()) {
                try {
                    fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), PrisonCore.getUid());
                } catch (Exception e) {
                    Logger.w(TAG, "Failed to fix attribution source state: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error in ContextCompat.fix: " + e.getMessage(), e);
        }
    }
}
