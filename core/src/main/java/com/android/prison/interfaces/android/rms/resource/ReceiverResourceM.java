package com.android.prison.interfaces.android.rms.resource;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.rms.resource.ReceiverResource")
public interface ReceiverResourceM {
    @BField
    String[] mWhiteList();
}
