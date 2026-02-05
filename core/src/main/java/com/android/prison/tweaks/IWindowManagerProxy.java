package com.android.prison.tweaks;

import android.os.IInterface;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.interfaces.android.view.BRIWindowManagerStub;
import com.android.prison.interfaces.android.view.BRWindowManagerGlobal;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;

public class IWindowManagerProxy extends BinderInvocationStub {
    public static final String TAG = IWindowManagerProxy.class.getSimpleName();

    public IWindowManagerProxy() {
        super(BRServiceManager.get().getService("window"));
    }

    @Override
    protected Object getWho() {
        return BRIWindowManagerStub.get().asInterface(BRServiceManager.get().getService("window"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("window");
        BRWindowManagerGlobal.get()._set_sWindowManagerService(null);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("openSession")
    public static class OpenSession extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface session = (IInterface) method.invoke(who, args);
            IWindowSessionProxy IWindowSessionProxy = new IWindowSessionProxy(session);
            IWindowSessionProxy.inject();
            return IWindowSessionProxy.getProxyInvocation();
        }
    }
}
