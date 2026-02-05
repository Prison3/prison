package com.android.prison.interfaces.android.app;

import android.content.Intent;
import android.os.IBinder;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BConstructor;
import com.android.reflection.annotation.BMethod;

@BClassName("android.app.PendingIntent")
public interface PendingIntentJBMR2 {
    @BConstructor
    PendingIntentJBMR2 _new(IBinder IBinder0);

    @BMethod
    Intent getIntent();
}
