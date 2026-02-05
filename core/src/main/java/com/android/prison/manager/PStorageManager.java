package com.android.prison.manager;

import android.net.Uri;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import com.android.prison.system.ServiceManager;
import com.android.prison.system.os.IPStorageManagerService;

public class PStorageManager extends Manager<IPStorageManagerService> {
    private static final PStorageManager sStorageManager = new PStorageManager();

    public static PStorageManager get() {
        return sStorageManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.STORAGE_MANAGER;
    }

    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) {
        try {
            return getService().getVolumeList(uid, packageName, flags, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new StorageVolume[]{};
    }

    public Uri getUriForFile(String file) {
        try {
            return getService().getUriForFile(file);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }
}
