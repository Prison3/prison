package com.android.prison.interfaces.android.net;

import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BConstructor;
import com.android.reflection.annotation.BField;

@BClassName("android.net.NetworkInfo")
public interface NetworkInfo {
    @BConstructor
    NetworkInfo _new(int int0, int int1, String String2, String String3);

    @BConstructor
    NetworkInfo _new(int int0);

    @BField
    DetailedState mDetailedState();

    @BField
    boolean mIsAvailable();

    @BField
    int mNetworkType();

    @BField
    State mState();

    @BField
    String mTypeName();
}
