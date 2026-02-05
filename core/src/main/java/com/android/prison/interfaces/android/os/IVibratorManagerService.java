package com.android.prison.interfaces.android.os;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

/**
 * Created by Prison on 2022/3/19.
 */
@BClassName("android.os.IVibratorManagerService")
public interface IVibratorManagerService {

    @BClassName("android.os.IVibratorManagerService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
