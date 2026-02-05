package com.android.prison.tweaks;

import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.android.prison.interfaces.android.os.BRIVibratorManagerServiceStub;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.interfaces.com.android.internal.os.BRIVibratorServiceStub;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.utils.MethodParameterUtils;
import com.android.prison.utils.BuildCompat;

/**
 * Created by Prison on 2022/3/7.
 */
public class IVibratorServiceProxy extends BinderInvocationStub {
    private static String NAME;
    static {
        if (BuildCompat.isS()) {
            NAME = "vibrator_manager";
        } else {
            NAME = Context.VIBRATOR_SERVICE;
        }
    }

    public IVibratorServiceProxy() {
        super(BRServiceManager.get().getService(NAME));
    }

    @Override
    protected Object getWho() {
        IBinder service = BRServiceManager.get().getService(NAME);
        if (BuildCompat.isS()) {
            return BRIVibratorManagerServiceStub.get().asInterface(service);
        }
        return BRIVibratorServiceStub.get().asInterface(service);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }
}
