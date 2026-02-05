package com.android.prison.interfaces.android.webkit;


import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BMethod;

@BClassName("android.webkit.IWebViewUpdateService")
public interface IWebViewUpdateService {
    @BMethod
    String getCurrentWebViewPackageName();

    @BMethod
    Object waitForAndGetProvider();
}
