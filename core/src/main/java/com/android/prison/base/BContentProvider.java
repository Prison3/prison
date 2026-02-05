package com.android.prison.base;

import android.os.IInterface;

public interface BContentProvider {
    IInterface wrapper(final IInterface contentProviderProxy, final String appPkg);
}
