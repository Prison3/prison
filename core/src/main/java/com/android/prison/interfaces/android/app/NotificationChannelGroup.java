package com.android.prison.interfaces.android.app;

import java.util.List;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.app.NotificationChannelGroup")
public interface NotificationChannelGroup {
    @BField
    List<android.app.NotificationChannel> mChannels();

    @BField
    String mId();
}
