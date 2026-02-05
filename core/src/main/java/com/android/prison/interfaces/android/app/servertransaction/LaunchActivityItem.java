package com.android.prison.interfaces.android.app.servertransaction;

import android.content.Intent;
import android.content.pm.ActivityInfo;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.app.servertransaction.LaunchActivityItem")
public interface LaunchActivityItem {
    @BField
    ActivityInfo mInfo();

    @BField
    Intent mIntent();
}
