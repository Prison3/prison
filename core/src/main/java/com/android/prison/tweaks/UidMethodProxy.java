package com.android.prison.tweaks;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.PActivityThread;

/**
 * Created by Prison on 2022/3/5.
 */
public class UidMethodProxy extends MethodHook {
    private final int index;
    private final String name;

    public UidMethodProxy(String name, int index) {
        this.index = index;
        this.name = name;
    }

    @Override
    protected String getMethodName() {
        return name;
    }

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int uid = (int) args[index];
        if (uid == PActivityThread.getBoundUid()) {
            args[index] = PrisonCore.getUid();
        }
        return method.invoke(who, args);
    }
}
