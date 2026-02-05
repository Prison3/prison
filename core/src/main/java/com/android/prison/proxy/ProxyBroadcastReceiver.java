package com.android.prison.proxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.android.prison.entity.PendingResultData;
import com.android.prison.manager.PActivityManager;

/**
 * Created by Prison on 2022/2/25.
 */
public class ProxyBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = ProxyBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setExtrasClassLoader(context.getClassLoader());
        ProxyBroadcastRecord record = ProxyBroadcastRecord.create(intent);
        if (record.mIntent == null) {
            return;
        }
        PendingResult pendingResult = goAsync();
        PActivityManager.get().scheduleBroadcastReceiver(record.mIntent, new PendingResultData(pendingResult), record.mUserId);
    }
}