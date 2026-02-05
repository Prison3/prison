package com.android.prison.tweaks;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.interfaces.android.view.accessibility.BRIAccessibilityManagerStub;
import com.android.prison.core.PrisonCore;
import com.android.prison.system.user.BUserHandle;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethods;

public class IAccessibilityManagerProxy extends BinderInvocationStub {

    public IAccessibilityManagerProxy() {
        super(BRServiceManager.get().getService(Context.ACCESSIBILITY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAccessibilityManagerStub.get().asInterface(BRServiceManager.get().getService(Context.ACCESSIBILITY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethods({"interrupt", "sendAccessibilityEvent", "addClient",
            "getInstalledAccessibilityServiceList", "getEnabledAccessibilityServiceList",
            "addAccessibilityInteractionConnection", "getWindowToken"})
    public static class ReplaceUserId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                int index = args.length - 1;
                Object arg = args[index];
                if (arg instanceof Integer) {
                    ApplicationInfo applicationInfo = PrisonCore.getContext().getApplicationInfo();
                    args[index] = BUserHandle.getUserId(applicationInfo.uid);
                }
            }
            return method.invoke(who, args);
        }
    }
}
