package com.android.prison.interfaces.com.android.internal.app;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("com.android.internal.app.ISmtOpsService")
public interface ISmtOpsService {
    @BClassName("com.android.internal.app.ISmtOpsService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
