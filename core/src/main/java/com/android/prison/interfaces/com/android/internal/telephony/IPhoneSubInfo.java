package com.android.prison.interfaces.com.android.internal.telephony;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("com.android.internal.telephony.IPhoneSubInfo")
public interface IPhoneSubInfo {
    @BClassName("com.android.internal.telephony.IPhoneSubInfo$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
