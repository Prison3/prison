package com.android.prison.tweaks;

import android.content.Context;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.media.session.BRISessionManagerStub;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;

public class IMediaSessionManagerProxy extends BinderInvocationStub {

    public IMediaSessionManagerProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_SESSION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRISessionManagerStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_SESSION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("createSession")
    public static class CreateSession extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                args[0] = PrisonCore.getPackageName();
            }
            return method.invoke(who, args);
        }
    }
}
