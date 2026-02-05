package com.android.prison.tweaks;

import android.app.ActivityManager;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.app.BRActivityTaskManager;
import com.android.prison.interfaces.android.app.BRIActivityTaskManagerStub;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.interfaces.android.util.BRSingleton;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;
import com.android.prison.base.ScanClass;
import com.android.prison.utils.TaskDescriptionCompat;

@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityTaskManagerProxy extends BinderInvocationStub {
    public static final String TAG = IActivityTaskManagerProxy.class.getSimpleName();

    public IActivityTaskManagerProxy() {
        super(BRServiceManager.get().getService("activity_task"));
    }

    @Override
    protected Object getWho() {
        return BRIActivityTaskManagerStub.get().asInterface(BRServiceManager.get().getService("activity_task"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("activity_task");
        BRActivityTaskManager.get().getService();
        Object o = BRActivityTaskManager.get().IActivityTaskManagerSingleton();
        BRSingleton.get(o)._set_mInstance(BRIActivityTaskManagerStub.get().asInterface(this));
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // for >= Android 10 && < Android 12
    @ProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
            args[1] = TaskDescriptionCompat.fix(td);
            return method.invoke(who, args);
        }
    }
}
