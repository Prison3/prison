package com.android.prison.tweaks;

import android.content.ComponentName;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.interfaces.android.view.BRIAutoFillManagerStub;
import com.android.prison.base.PActivityThread;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;
import com.android.prison.proxy.ProxyManifest;

public class IAutofillManagerProxy extends BinderInvocationStub {
    public static final String TAG = IAutofillManagerProxy.class.getSimpleName();

    public IAutofillManagerProxy() {
        super(BRServiceManager.get().getService("autofill"));
    }

    @Override
    protected Object getWho() {
        return BRIAutoFillManagerStub.get().asInterface(BRServiceManager.get().getService("autofill"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("autofill");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("startSession")
    public static class StartSession extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null)
                        continue;
                    if (args[i] instanceof ComponentName) {
                        args[i] = new ComponentName(PrisonCore.getPackageName(), ProxyManifest.getProxyActivity(PActivityThread.getAppPid()));
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
}
