package com.android.prison.interfaces.android.app;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.IBinder;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;
import com.android.reflection.annotation.BMethod;

@BClassName("android.app.Activity")
public interface Activity {
    @BField
    ActivityInfo mActivityInfo();

    @BField
    String mEmbeddedID();

    @BField
    boolean mFinished();

    @BField
    android.app.Activity mParent();

    @BField
    int mResultCode();

    @BField
    Intent mResultData();

    @BField
    IBinder mToken();

    @BMethod
    void onActivityResult(int int0, int int1, Intent Intent2);
}
