package com.android.prison.interfaces.android.app;

import android.app.PendingIntent;
import android.content.Context;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BMethod;

@BClassName("android.app.Notification")
public interface Notification {
    @BMethod
    void setLatestEventInfo(Context Context0, CharSequence CharSequence1, CharSequence CharSequence2, PendingIntent PendingIntent3);
}
