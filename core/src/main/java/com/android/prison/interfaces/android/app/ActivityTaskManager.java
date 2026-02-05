package com.android.prison.interfaces.android.app;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticField;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.app.ActivityTaskManager")
public interface ActivityTaskManager {

    @BStaticMethod
    Object getService();

    @BStaticField
    Object IActivityTaskManagerSingleton();
}
