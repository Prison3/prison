package com.android.prison.interfaces.com.android.internal.content;

import java.io.File;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;
import com.android.reflection.annotation.BParamClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("com.android.internal.content.NativeLibraryHelper")
public interface NativeLibraryHelper {
    @BStaticMethod
    Integer copyNativeBinaries(Handle Handle0, File File1, String String2);

    @BStaticMethod
    Integer findSupportedAbi(Handle Handle0, @BParamClassName("[Ljava.lang.String;") String[] strings);

    @BClassName("com.android.internal.content.NativeLibraryHelper$Handle")
    interface Handle {
        @BField
        boolean extractNativeLibs();

        @BStaticMethod
        Object create(File File0);
    }
}
