package com.android.prison.interfaces.com.android.internal.os;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.os.IVibratorService")
public interface IVibratorService {
    @BClassName("android.os.IVibratorService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
