package com.android.prison.interfaces.android.app;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.app.LoadedApk")
public interface LoadedApkKitkat {
    @BField
    Object mDisplayAdjustments();
}
