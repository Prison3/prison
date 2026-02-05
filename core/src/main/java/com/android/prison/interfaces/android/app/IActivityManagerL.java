package com.android.prison.interfaces.android.app;

import android.content.Intent;
import android.os.IBinder;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BMethod;

@BClassName("android.app.IActivityManager")
public interface IActivityManagerL {
    @BMethod
    Boolean finishActivity(IBinder IBinder0, int int1, Intent Intent2, boolean boolean3);
}
