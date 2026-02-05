package com.android.prison.interfaces.android.app.role;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.app.role.IRoleManager")
public interface IRoleManager {
    @BClassName("android.app.role.IRoleManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
