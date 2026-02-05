package com.android.prison.interfaces.android.content;

import java.util.List;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.content.IntentFilter")
public interface IntentFilter {
    @BField
    List<String> mActions();

    @BField
    List<String> mCategories();
}
