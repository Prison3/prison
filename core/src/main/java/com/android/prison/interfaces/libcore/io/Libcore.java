package com.android.prison.interfaces.libcore.io;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticField;

@BClassName("libcore.io.Libcore")
public interface Libcore {
    @BStaticField
    Object os();
}
