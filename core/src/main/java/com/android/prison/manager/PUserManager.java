package com.android.prison.manager;

import android.os.DeadObjectException;
import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

import com.android.prison.system.ServiceManager;
import com.android.prison.system.user.PUserInfo;
import com.android.prison.system.user.IPUserManagerService;
import com.android.prison.utils.Logger;

public class PUserManager extends Manager<IPUserManagerService> {
    private static final String TAG = PUserManager.class.getSimpleName();
    private static final PUserManager sUserManager = new PUserManager();

    public static PUserManager get() {
        return sUserManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.USER_MANAGER;
    }

    public PUserInfo createUser(int userId) {
        try {
            IPUserManagerService service = getService();
            if (service != null) {
                return service.createUser(userId);
            } else {
                Logger.w(TAG, "UserManager service is null, cannot create user");
            }
        } catch (DeadObjectException e) {
            Logger.w(TAG, "UserManager service died during createUser, clearing cache and retrying", e);
            clearServiceCache();
            try {
                Thread.sleep(100);
                IPUserManagerService service = getService();
                if (service != null) {
                    return service.createUser(userId);
                }
            } catch (Exception retryException) {
                Logger.e(TAG, "Failed to create user after retry", retryException);
            }
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in createUser", e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in createUser", e);
        }
        return null;
    }

    public void deleteUser(int userId) {
        try {
            IPUserManagerService service = getService();
            if (service != null) {
                service.deleteUser(userId);
            } else {
                Logger.w(TAG, "UserManager service is null, cannot delete user");
            }
        } catch (DeadObjectException e) {
            Logger.w(TAG, "UserManager service died during deleteUser, clearing cache and retrying", e);
            clearServiceCache();
            try {
                Thread.sleep(100);
                IPUserManagerService service = getService();
                if (service != null) {
                    service.deleteUser(userId);
                }
            } catch (Exception retryException) {
                Logger.e(TAG, "Failed to delete user after retry", retryException);
            }
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in deleteUser", e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in deleteUser", e);
        }
    }

    public List<PUserInfo> getUsers() {
        try {
            IPUserManagerService service = getService();
            if (service != null) {
                return service.getUsers();
            } else {
                Logger.w(TAG, "UserManager service is null, returning empty list");
            }
        } catch (DeadObjectException e) {
            Logger.w(TAG, "UserManager service died during getUsers, clearing cache and retrying", e);
            clearServiceCache();
            try {
                Thread.sleep(100);
                IPUserManagerService service = getService();
                if (service != null) {
                    return service.getUsers();
                }
            } catch (Exception retryException) {
                Logger.e(TAG, "Failed to get users after retry", retryException);
            }
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getUsers", e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in getUsers", e);
        }
        return Collections.emptyList();
    }
}
