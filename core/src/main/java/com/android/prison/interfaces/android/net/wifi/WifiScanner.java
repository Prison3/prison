package com.android.prison.interfaces.android.net.wifi;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticField;

@BClassName("android.net.wifi.WifiScanner")
public interface WifiScanner {
    @BStaticField
    String GET_AVAILABLE_CHANNELS_EXTRA();
}
