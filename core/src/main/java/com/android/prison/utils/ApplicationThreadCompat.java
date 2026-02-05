package com.android.prison.utils;

import android.os.IBinder;
import android.os.IInterface;

import com.android.prison.interfaces.android.app.BRApplicationThreadNative;
import com.android.prison.interfaces.android.app.BRIApplicationThreadOreoStub;

public class ApplicationThreadCompat {

    public static IInterface asInterface(IBinder binder) {
        if (BuildCompat.isOreo()) {
            return BRIApplicationThreadOreoStub.get().asInterface(binder);
        }
        return BRApplicationThreadNative.get().asInterface(binder);
    }
}
