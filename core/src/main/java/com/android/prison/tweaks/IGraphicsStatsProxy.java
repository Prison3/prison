package com.android.prison.tweaks;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.interfaces.android.view.BRIGraphicsStatsStub;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.MethodParameterUtils;

public class IGraphicsStatsProxy extends BinderInvocationStub {

    public IGraphicsStatsProxy() {
        super(BRServiceManager.get().getService("graphicsstats"));
    }

    @Override
    protected Object getWho() {
        return BRIGraphicsStatsStub.get().asInterface(BRServiceManager.get().getService("graphicsstats"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("graphicsstats");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("requestBufferForProcess")
    public static class RequestBufferForProcess extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
