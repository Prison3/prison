package com.android.prison.interfaces.android.security.net.config;

import android.content.Context;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.security.net.config.NetworkSecurityConfigProvider")
public interface NetworkSecurityConfigProvider {
    @BStaticMethod
    void install(Context Context0);
}
