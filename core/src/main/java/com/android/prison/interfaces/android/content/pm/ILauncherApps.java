package com.android.prison.interfaces.android.content.pm;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("mirror.android.content.pm.ILauncherApps")
public interface ILauncherApps {
    @BClassName("android.content.pm.ILauncherApps$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder binder);
    }
}
