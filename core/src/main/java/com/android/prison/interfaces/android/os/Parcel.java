package com.android.prison.interfaces.android.os;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticField;

@BClassName("android.os.Parcel")
public interface Parcel {
    @BStaticField
    int VAL_PARCELABLE();

    @BStaticField
    int VAL_PARCELABLEARRAY();
}
