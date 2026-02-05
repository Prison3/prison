package com.android.prison.interfaces.android.location;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;
import com.android.reflection.annotation.BMethod;

@BClassName("android.location.LocationRequest")
public interface LocationRequestL {
    @BField
    boolean mHideFromAppOps();

    @BField
    String mProvider();

    @BField
    Object mWorkSource();

    @BMethod
    String getProvider();
}
