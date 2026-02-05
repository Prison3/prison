package com.android.prison.interfaces.android.content;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.content.IRestrictionsManager")
public interface IRestrictionsManager {
    @BClassName("android.content.IRestrictionsManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
