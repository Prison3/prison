package com.android.prison.tweaks;

import com.android.prison.interfaces.android.net.BRIVpnManagerStub;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ScanClass;

@ScanClass(VpnCommonProxy.class)
public class IVpnManagerProxy extends BinderInvocationStub {
    public static final String TAG = IVpnManagerProxy.class.getSimpleName();
    public static final String VPN_MANAGEMENT_SERVICE = "vpn_management";

    public IVpnManagerProxy() {
        super(BRServiceManager.get().getService(VPN_MANAGEMENT_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIVpnManagerStub.get().asInterface(BRServiceManager.get().getService(VPN_MANAGEMENT_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(VPN_MANAGEMENT_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
