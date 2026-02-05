package com.android.prison.base;

import android.os.IInterface;

public interface IContentProvider {
    IInterface wrapper(final IInterface contentProviderProxy, final String appPkg);
}
