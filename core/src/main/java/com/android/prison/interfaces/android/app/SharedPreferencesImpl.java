package com.android.prison.interfaces.android.app;

import java.io.File;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BConstructor;

@BClassName("android.app.SharedPreferencesImpl")
public interface SharedPreferencesImpl {
    @BConstructor
    SharedPreferencesImpl _new(File File0, int int1);
}
