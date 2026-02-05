package com.android.prison.interfaces.android.app.admin;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.app.admin.IDevicePolicyManager")
public interface IDevicePolicyManager {
    @BClassName("android.app.admin.IDevicePolicyManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
