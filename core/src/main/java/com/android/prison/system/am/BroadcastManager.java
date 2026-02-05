package com.android.prison.system.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.prison.core.PrisonCore;
import com.android.prison.system.pm.BPackage;
import com.android.prison.system.pm.PackageManagerService;
import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.system.pm.PackageMonitor;
import com.android.prison.entity.PendingResultData;
import com.android.prison.proxy.ProxyBroadcastReceiver;
import com.android.prison.utils.Logger;

/**
 * Created by Prison on 2022/2/28.
 */
public class BroadcastManager implements PackageMonitor {
    public static final String TAG = BroadcastManager.class.getSimpleName();

    public static final int TIMEOUT = 9000;

    public static final int MSG_TIME_OUT = 1;

    private static BroadcastManager sBroadcastManager;

    private final ActivityManagerService mAms;
    private final PackageManagerService mPms;
    private final Map<String, List<BroadcastReceiver>> mReceivers = new HashMap<>();
    private final Map<String, PendingResultData> mReceiversData = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_TIME_OUT:
                    try {
                        PendingResultData data = (PendingResultData) msg.obj;
                        data.build().finish();
                        Logger.d(TAG, "Timeout Receiver: " + data);
                    } catch (Throwable ignore) {
                    }
                    break;
            }
        }
    };

    public static BroadcastManager startSystem(ActivityManagerService ams, PackageManagerService pms) {
        if (sBroadcastManager == null) {
            synchronized (BroadcastManager.class) {
                if (sBroadcastManager == null) {
                    sBroadcastManager = new BroadcastManager(ams, pms);
                }
            }
        }
        return sBroadcastManager;
    }

    public BroadcastManager(ActivityManagerService ams, PackageManagerService pms) {
        mAms = ams;
        mPms = pms;
    }

    public void startup() {
        mPms.addPackageMonitor(this);
        List<PPackageSettings> bPackageSettings = mPms.getPPackageSettings();
        for (PPackageSettings bPackageSetting : bPackageSettings) {
            BPackage bPackage = bPackageSetting.pkg;
            registerPackage(bPackage);
        }
    }

    private void registerPackage(BPackage bPackage) {
        synchronized (mReceivers) {
            Logger.d(TAG, "register: " + bPackage.packageName + ", size: " + bPackage.receivers.size());
            for (BPackage.Activity receiver : bPackage.receivers) {
                List<BPackage.ActivityIntentInfo> intents = receiver.intents;
                for (BPackage.ActivityIntentInfo intent : intents) {
                    ProxyBroadcastReceiver proxyBroadcastReceiver = new ProxyBroadcastReceiver();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        PrisonCore.getContext().registerReceiver(proxyBroadcastReceiver, intent.intentFilter, Context.RECEIVER_EXPORTED);
                    }else{
                        PrisonCore.getContext().registerReceiver(proxyBroadcastReceiver, intent.intentFilter);
                    }
                    addReceiver(bPackage.packageName, proxyBroadcastReceiver);
                }
            }
        }
    }

    private void addReceiver(String packageName, BroadcastReceiver receiver) {
        List<BroadcastReceiver> broadcastReceivers = mReceivers.get(packageName);
        if (broadcastReceivers == null) {
            broadcastReceivers = new ArrayList<>();
            mReceivers.put(packageName, broadcastReceivers);
        }
        broadcastReceivers.add(receiver);
    }

    public void sendBroadcast(PendingResultData pendingResultData) {
        synchronized (mReceiversData) {
            // Logger.d(TAG, "sendBroadcast: " + pendingResultData);
            mReceiversData.put(pendingResultData.mBToken, pendingResultData);
            Message obtain = Message.obtain(mHandler, MSG_TIME_OUT, pendingResultData);
            mHandler.sendMessageDelayed(obtain, TIMEOUT);
        }
    }

    public void finishBroadcast(PendingResultData data) {
        synchronized (mReceiversData) {
            // Logger.d(TAG, "finishBroadcast: " + data);
            mHandler.removeMessages(MSG_TIME_OUT, mReceiversData.get(data.mBToken));
        }
    }

    @Override
    public void onPackageUninstalled(String packageName, boolean removeApp, int userId) {
        if (removeApp) {
            synchronized (mReceivers) {
                List<BroadcastReceiver> broadcastReceivers = mReceivers.get(packageName);
                if (broadcastReceivers != null) {
                    Logger.d(TAG, "unregisterReceiver Package: " + packageName + ", size: " + broadcastReceivers.size());
                    for (BroadcastReceiver broadcastReceiver : broadcastReceivers) {
                        try {
                            PrisonCore.getContext().unregisterReceiver(broadcastReceiver);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                mReceivers.remove(packageName);
            }
        }
    }

    @Override
    public void onPackageInstalled(String packageName, int userId) {
        synchronized (mReceivers) {
            mReceivers.remove(packageName);
            PPackageSettings bPackageSetting = mPms.getBPackageSetting(packageName);
            if (bPackageSetting != null) {
                registerPackage(bPackageSetting.pkg);
            }
        }
    }
}
