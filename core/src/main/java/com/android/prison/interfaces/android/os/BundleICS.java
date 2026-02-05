package com.android.prison.interfaces.android.os;

import android.os.Parcel;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.os.Bundle")
public interface BundleICS {
    @BField
    Parcel mParcelledData();
}
