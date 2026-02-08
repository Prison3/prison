package com.android.prison.tweaks;

import android.content.Context;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.media.BRIMediaRouterServiceStub;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.MethodParameterUtils;

/**
 * Created by Prison on 2022/3/1.
 */
public class IMediaRouterServiceProxy extends BinderInvocationStub {

    public IMediaRouterServiceProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIMediaRouterServiceStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_ROUTER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("registerClientAsUser")
    public static class registerClientAsUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("registerRouter2")
    public static class registerRouter2 extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    /**
     * Hook getSystemRoutes to fix SecurityException: callerPackageName does not match calling uid.
     * When running inside Prison, the Binder calling uid is the host app's uid, but the request
     * may carry the virtual app's package name. Replacing package name in args with host package
     * so that MediaRouterService's validation passes.
     */
    @ProxyMethod("getSystemRoutes")
    public static class getSystemRoutes extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
