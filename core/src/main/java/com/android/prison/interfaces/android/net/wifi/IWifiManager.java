package com.android.prison.interfaces.android.net.wifi;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.net.wifi.IWifiManager")
public interface IWifiManager {
    @BClassName("android.net.wifi.IWifiManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
