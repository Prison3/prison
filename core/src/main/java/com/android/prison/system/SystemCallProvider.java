package com.android.prison.system;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.prison.utils.BundleCompat;
import com.android.prison.utils.Logger;

public class SystemCallProvider extends ContentProvider {
    public static final String TAG = SystemCallProvider.class.getSimpleName();

    @Override
    public boolean onCreate() {
        try {
            Logger.d(TAG, "SystemCallProvider onCreate called");
            ServiceManager.get().startup();
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Error in SystemCallProvider onCreate", e);
            return false;
        }
    }



    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        try {
            Logger.d(TAG, "call: " + method + ", " + extras);
            if ("VM".equals(method)) {
                Bundle bundle = new Bundle();
                if (extras != null) {
                    String name = extras.getString("_B_|_server_name_");
                    Logger.d(TAG, "Requesting service: " + name);
                    IBinder service = ServiceManager.getService(name);
                    if (service != null) {
                        BundleCompat.putBinder(bundle, "_B_|_server_", service);
                        Logger.d(TAG, "Service " + name + " provided successfully");
                    } else {
                        Logger.w(TAG, "Service " + name + " not found");
                    }
                }
                return bundle;
            }
            return super.call(method, arg, extras);
        } catch (Exception e) {
            Logger.e(TAG, "Error in SystemCallProvider call method: " + method, e);
            // Return a bundle with error information
            Bundle errorBundle = new Bundle();
            errorBundle.putString("error", e.getMessage());
            return errorBundle;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
