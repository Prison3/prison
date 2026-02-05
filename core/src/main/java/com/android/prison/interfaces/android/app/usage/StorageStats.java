package com.android.prison.interfaces.android.app.usage;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BConstructor;
import com.android.reflection.annotation.BField;

@BClassName("android.app.usage.StorageStats")
public interface StorageStats {
    @BConstructor
    StorageStats _new();

    @BField
    long cacheBytes();

    @BField
    long codeBytes();

    @BField
    long dataBytes();
}
