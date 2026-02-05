package com.android.prison.interfaces.android.app;

import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;
import com.android.reflection.annotation.BMethod;
import com.android.reflection.annotation.BParamClassName;

@BClassName("android.app.IActivityManager")
public interface IActivityManager {
    @BMethod
    Integer getTaskForActivity(IBinder IBinder0, boolean boolean1);

    @BMethod
    void overridePendingTransition(IBinder IBinder0, String String1, int int2, int int3);

    @BMethod
    void setRequestedOrientation(IBinder IBinder0, int int1);

    @BMethod
    Integer startActivities();

    @BMethod
    Integer startActivity(@BParamClassName("android.app.IApplicationThread") Object caller, String callingPackage,
                          Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
                          int startFlags, @BParamClassName("android.app.ProfilerInfo") Object profilerInfo, Bundle bOptions);

    @BClassName("android.app.IActivityManager$ContentProviderHolder")
    interface ContentProviderHolderMIUI {
        @BField
        ProviderInfo info();

        @BField
        boolean noReleaseNeeded();

        @BField
        IInterface provider();

        @BField
        boolean waitProcessStart();
    }

    @BClassName("android.app.IActivityManager$ContentProviderHolder")
    interface ContentProviderHolder {
        @BField
        ProviderInfo info();

        @BField
        boolean noReleaseNeeded();

        @BField
        IInterface provider();
    }
}
