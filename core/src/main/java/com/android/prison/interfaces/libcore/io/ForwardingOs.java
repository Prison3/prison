package com.android.prison.interfaces.libcore.io;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("libcore.io.ForwardingOs")
public interface ForwardingOs {
    @BField
    Object os();
}
