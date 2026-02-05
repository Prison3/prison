package com.android.prison.interfaces.android.app;

import android.content.ComponentName;
import android.os.IBinder;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BMethod;

@BClassName("android.app.IServiceConnection")
public interface IServiceConnectionO {
    @BMethod
    void connected(ComponentName ComponentName0, IBinder IBinder1, boolean boolean2);
}
