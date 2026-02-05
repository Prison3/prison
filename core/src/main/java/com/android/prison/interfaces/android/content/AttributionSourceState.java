package com.android.prison.interfaces.android.content;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

/**
 * Created by Prison on 2022/2/20.
 */
@BClassName("android.content.AttributionSourceState")
public interface AttributionSourceState {
    @BField
    String packageName();

    @BField
    int uid();
}
