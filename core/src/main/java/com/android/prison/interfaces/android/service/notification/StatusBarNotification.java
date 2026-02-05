package com.android.prison.interfaces.android.service.notification;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

@BClassName("android.service.notification.StatusBarNotification")
public interface StatusBarNotification {
    @BField
    Integer id();

    @BField
    String opPkg();

    @BField
    String pkg();

    @BField
    String tag();
}
