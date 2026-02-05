package com.android.prison.interfaces.android.view;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BMethod;

@BClassName("android.view.DisplayAdjustments")
public interface DisplayAdjustments {
    @BMethod
    void setCompatibilityInfo();
}
