package com.android.prison.interfaces.android.graphics;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

/**
 * Created by Prison on 2022/2/24.
 */
@BClassName("android.graphics.Compatibility")
public interface Compatibility {
    @BStaticMethod
    void setTargetSdkVersion(int targetSdkVersion);
}
