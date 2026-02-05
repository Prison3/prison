package com.android.prison.tweaks;

import android.os.IInterface;

import java.lang.reflect.Method;

import com.android.prison.base.IContentProvider;
import com.android.prison.interfaces.android.content.BRAttributionSource;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.ClassInvocationStub;
import com.android.prison.utils.ContextCompat;

public class SystemProviderStub extends ClassInvocationStub implements IContentProvider {
    private IInterface mBase;

    @Override
    public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
        mBase = contentProviderProxy;
        inject();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }

        String methodName = method.getName();

        // For call() method, args[0] is the method name (like "GET_global"), NOT a package name
        // Don't replace it! The method name is essential for Settings provider to work
        if ("call".equals(methodName)) {
            // Only fix AttributionSource in call() args, don't replace method name
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg != null && arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
                        ContextCompat.fixAttributionSourceState(arg, PrisonCore.getUid());
                    }
                }
            }
            return method.invoke(mBase, args);
        }

        // For other methods like query/insert/update/delete, we may need to fix package names
        if (args != null && args.length > 0) {
            Object arg = args[0];
            if (arg instanceof String) {
                String authority = (String) arg;
                // Only replace if it's not a system provider authority
                if (!isSystemProviderAuthority(authority)) {
                    args[0] = PrisonCore.getPackageName();
                }
            } else if (arg != null && arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
                ContextCompat.fixAttributionSourceState(arg, PrisonCore.getUid());
            }
        }
        return method.invoke(mBase, args);
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        // Common system provider authorities that should not be replaced
        return authority.equals("settings") ||
                authority.equals("media") ||
                authority.equals("downloads") ||
                authority.equals("contacts") ||
                authority.equals("call_log") ||
                authority.equals("telephony") ||
                authority.equals("calendar") ||
                authority.equals("browser") ||
                authority.equals("user_dictionary") ||
                authority.equals("applications") ||
                authority.startsWith("com.android.") ||
                authority.startsWith("android.");
    }
}
