package com.android.prison.interfaces.com.android.internal.net;

import java.util.List;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BField;

/**
 * Created by Prison on 2022/2/25.
 */
@BClassName("com.android.internal.net.VpnConfig")
public interface VpnConfig {
    @BField
    String user();

    @BField
    List<String> disallowedApplications();

    @BField
    List<String> allowedApplications();
}
