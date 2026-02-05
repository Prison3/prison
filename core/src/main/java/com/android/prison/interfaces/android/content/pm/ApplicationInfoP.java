package com.android.prison.interfaces.android.content.pm;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;
import com.android.reflection.annotation.BMethod;

@BClassName("android.content.pm.ApplicationInfo")
public interface ApplicationInfoP {
    @BField
    String[] splitClassLoaderNames();

    @BMethod
    void setHiddenApiEnforcementPolicy(int int0);
}
