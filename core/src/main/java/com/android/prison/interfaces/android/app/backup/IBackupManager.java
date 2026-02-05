package com.android.prison.interfaces.android.app.backup;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

@BClassName("android.app.backup.IBackupManager")
public interface IBackupManager {
    @BClassName("android.app.backup.IBackupManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
