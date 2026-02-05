package com.android.prison.interfaces.android.telephony;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BConstructor;
import com.android.reflection.annotation.BField;

@BClassName("android.telephony.CellIdentityCdma")
public interface CellIdentityCdma {
    @BConstructor
    CellIdentityCdma _new();

    @BField
    int mBasestationId();

    @BField
    int mNetworkId();

    @BField
    int mSystemId();
}
