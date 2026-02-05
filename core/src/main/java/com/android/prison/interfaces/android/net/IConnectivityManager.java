package com.android.prison.interfaces.android.net;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.net.IConnectivityManager")
public interface IConnectivityManager {
    @BClassName("android.net.IConnectivityManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
