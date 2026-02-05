package com.android.prison.interfaces.android.app;

import android.os.IBinder;

import java.util.List;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BMethod;

@BClassName("android.app.ActivityThread")
public interface ActivityThreadNMR1 {
    @BMethod
    void performNewIntents(IBinder IBinder0, List List1, boolean boolean2);
}
