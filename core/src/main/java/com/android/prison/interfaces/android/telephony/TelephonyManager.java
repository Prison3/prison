package com.android.prison.interfaces.android.telephony;

import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticField;
import com.android.reflection.annotation.BStaticMethod;

/**
 * Created by Prison on 2022/2/26.
 */
@BClassName("android.telephony.TelephonyManager")
public interface TelephonyManager {

    @BStaticMethod
    Object getSubscriberInfoService();

    @BStaticField
    boolean sServiceHandleCacheEnabled();

    @BStaticField
    IInterface sIPhoneSubInfo();
}
