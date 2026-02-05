package com.android.prison.system.os;

import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import java.io.File;

import com.android.prison.interfaces.android.os.storage.BRStorageManager;
import com.android.prison.interfaces.android.os.storage.BRStorageVolume;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.PEnvironment;
import com.android.prison.system.ISystemService;
import com.android.prison.system.user.BUserHandle;
import com.android.prison.base.FileProvider;
import com.android.prison.proxy.ProxyManifest;
import com.android.prison.utils.BuildCompat;

public class StorageManagerService extends IPStorageManagerService.Stub implements ISystemService {
    private static final StorageManagerService sService = new StorageManagerService();

    public static StorageManagerService get() {
        return sService;
    }

    public StorageManagerService() {
    }

    @Override
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) throws RemoteException {
        if (BRStorageManager.get().getVolumeList(0, 0) == null) {
            return null;
        }
        try {
            StorageVolume[] storageVolumes = BRStorageManager.get().getVolumeList(BUserHandle.getUserId(Process.myUid()), 0);
            if (storageVolumes == null)
                return null;
            for (StorageVolume storageVolume : storageVolumes) {
                BRStorageVolume.get(storageVolume)._set_mPath(PEnvironment.getExternalUserDir(userId));
                if (BuildCompat.isPie()) {
                    BRStorageVolume.get(storageVolume)._set_mInternalPath(PEnvironment.getExternalUserDir(userId));
                }
            }
            return storageVolumes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Uri getUriForFile(String file) throws RemoteException {
        return FileProvider.getUriForFile(PrisonCore.getContext(), ProxyManifest.getProxyFileProvider(), new File(file));
    }

    @Override
    public void systemReady() {

    }
}
