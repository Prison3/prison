package com.android.prison.base;

import android.net.Uri;
import android.os.Build;
import android.os.IInterface;
import android.util.ArrayMap;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import com.android.prison.interfaces.android.app.BRActivityThread;
import com.android.prison.interfaces.android.app.BRActivityThreadProviderClientRecordP;
import com.android.prison.interfaces.android.app.BRIActivityManagerContentProviderHolder;
import com.android.prison.interfaces.android.content.BRContentProviderHolderOreo;
import com.android.prison.interfaces.android.providers.BRSettingsContentProviderHolder;
import com.android.prison.interfaces.android.providers.BRSettingsGlobal;
import com.android.prison.interfaces.android.providers.BRSettingsNameValueCache;
import com.android.prison.interfaces.android.providers.BRSettingsNameValueCacheOreo;
import com.android.prison.interfaces.android.providers.BRSettingsSecure;
import com.android.prison.interfaces.android.providers.BRSettingsSystem;
import com.android.prison.core.PrisonCore;
import com.android.prison.tweaks.ContentProviderStub;
import com.android.prison.tweaks.SystemProviderStub;
import com.android.prison.utils.BuildCompat;

public class ContentProviderDelegate {
    public static final String TAG = ContentProviderDelegate.class.getSimpleName();
    private static Set<String> sInjected = new HashSet<>();

    public static void update(Object holder, String auth) {
        IInterface iInterface;
        if (BuildCompat.isOreo()) {
            iInterface = BRContentProviderHolderOreo.get(holder).provider();
        } else {
            iInterface = BRIActivityManagerContentProviderHolder.get(holder).provider();
        }

        if (iInterface instanceof Proxy)
            return;
        IInterface bContentProvider;
        switch (auth) {
            case "media":
            case "telephony":
            case "settings":
                bContentProvider = new SystemProviderStub().wrapper(iInterface, PrisonCore.getPackageName());
                break;
            default:
                bContentProvider = new ContentProviderStub().wrapper(iInterface, PrisonCore.getPackageName());
                break;
        }
        if (BuildCompat.isOreo()) {
            BRContentProviderHolderOreo.get(holder)._set_provider(bContentProvider);
        } else {
            BRIActivityManagerContentProviderHolder.get(holder)._set_provider(bContentProvider);
        }
    }

    public static void init() {
        clearSettingProvider();

        PrisonCore.getContext().getContentResolver().call(Uri.parse("content://settings"), "", null, null);
        Object activityThread = PrisonCore.mainThread();
        ArrayMap<Object, Object> map = (ArrayMap<Object, Object>) BRActivityThread.get(activityThread).mProviderMap();

        for (Object value : map.values()) {
            String[] mNames = BRActivityThreadProviderClientRecordP.get(value).mNames();
            if (mNames == null || mNames.length <= 0) {
                continue;
            }
            String providerName = mNames[0];
            if (!sInjected.contains(providerName)) {
                sInjected.add(providerName);
                final IInterface iInterface = BRActivityThreadProviderClientRecordP.get(value).mProvider();
                BRActivityThreadProviderClientRecordP.get(value)._set_mProvider(new ContentProviderStub().wrapper(iInterface, PrisonCore.getPackageName()));
                BRActivityThreadProviderClientRecordP.get(value)._set_mNames(new String[]{providerName});
            }
        }
    }

    public static void clearSettingProvider() {
        Object cache;
        cache = BRSettingsSystem.get().sNameValueCache();
        if (cache != null) {
            clearContentProvider(cache);
        }
        cache = BRSettingsSecure.get().sNameValueCache();
        if (cache != null) {
            clearContentProvider(cache);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && BRSettingsGlobal.getRealClass() != null) {
            cache = BRSettingsGlobal.get().sNameValueCache();
            if (cache != null) {
                clearContentProvider(cache);
            }
        }
    }

    private static void clearContentProvider(Object cache) {
        if (BuildCompat.isOreo()) {
            Object holder = BRSettingsNameValueCacheOreo.get(cache).mProviderHolder();
            if (holder != null) {
                BRSettingsContentProviderHolder.get(holder)._set_mContentProvider(null);
            }
        } else {
            BRSettingsNameValueCache.get(cache)._set_mContentProvider(null);
        }
    }
}
