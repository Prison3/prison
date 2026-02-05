package com.android.prison.interfaces.android.content;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;
import com.android.reflection.annotation.BMethod;

/**
 * Created by Prison on 2022/2/20.
 */
@BClassName("android.content.AttributionSource")
public interface AttributionSource {
    @BField
    Object mAttributionSourceState();

    @BMethod
    Object getNext();
}
