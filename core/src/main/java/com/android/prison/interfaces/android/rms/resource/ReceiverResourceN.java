package com.android.prison.interfaces.android.rms.resource;

import java.util.List;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.rms.resource.ReceiverResource")
public interface ReceiverResourceN {
    @BField
    List<String> mWhiteList();
}
