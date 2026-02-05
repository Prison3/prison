package com.android.prison.system;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.prison.core.PrisonCore;
import com.android.prison.base.AppSystemEnv;
import com.android.prison.base.PEnvironment;
import com.android.prison.system.accounts.AccountManagerService;
import com.android.prison.system.am.ActivityManagerService;
import com.android.prison.system.am.JobManagerService;
import com.android.prison.system.location.LocationManagerService;
import com.android.prison.system.notification.NotificationManagerService;
import com.android.prison.system.os.StorageManagerService;
import com.android.prison.system.pm.PackageInstallerService;
import com.android.prison.system.pm.PackageManagerService;
import com.android.prison.system.user.BUserHandle;
import com.android.prison.system.user.BUserManagerService;
import com.android.prison.entity.InstallOption;

public class ServiceManager {
    private static ServiceManager sServiceManager = null;
    public static final String ACTIVITY_MANAGER = "activity_manager";
    public static final String JOB_MANAGER = "job_manager";
    public static final String PACKAGE_MANAGER = "package_manager";
    public static final String STORAGE_MANAGER = "storage_manager";
    public static final String USER_MANAGER = "user_manager";
    public static final String ACCOUNT_MANAGER = "account_manager";
    public static final String LOCATION_MANAGER = "location_manager";
    public static final String NOTIFICATION_MANAGER = "notification_manager";

    private final Map<String, IBinder> mCaches = new HashMap<>();
    private final List<ISystemService> mServices = new ArrayList<>();
    private final static AtomicBoolean isStartup = new AtomicBoolean(false);

    public static ServiceManager get() {
        if (sServiceManager == null) {
            synchronized (ServiceManager.class) {
                if (sServiceManager == null) {
                    sServiceManager = new ServiceManager();
                }
            }
        }
        return sServiceManager;
    }

    public static IBinder getService(String name) {
        return get().getServiceInternal(name);
    }

    private ServiceManager() {
        mCaches.put(ACTIVITY_MANAGER, ActivityManagerService.get());
        mCaches.put(JOB_MANAGER, JobManagerService.get());
        mCaches.put(PACKAGE_MANAGER, PackageManagerService.get());
        mCaches.put(STORAGE_MANAGER, StorageManagerService.get());
        mCaches.put(USER_MANAGER, BUserManagerService.get());
        mCaches.put(ACCOUNT_MANAGER, AccountManagerService.get());
        mCaches.put(LOCATION_MANAGER, LocationManagerService.get());
        mCaches.put(NOTIFICATION_MANAGER, NotificationManagerService.get());
    }

    public IBinder getServiceInternal(String name) {
        return mCaches.get(name);
    }

    /**
     * Start up all system services and initialize the Prison system.
     * This method should be called once during system initialization.
     */
    public void startup() {
        if (isStartup.getAndSet(true)) {
            return;
        }
        PEnvironment.load();

        mServices.add(PackageManagerService.get());
        mServices.add(BUserManagerService.get());
        mServices.add(ActivityManagerService.get());
        mServices.add(JobManagerService.get());
        mServices.add(StorageManagerService.get());
        mServices.add(PackageInstallerService.get());
        mServices.add(ProcessManagerService.get());
        mServices.add(AccountManagerService.get());
        mServices.add(LocationManagerService.get());
        mServices.add(NotificationManagerService.get());

        for (ISystemService service : mServices) {
            service.systemReady();
        }

        List<String> preInstallPackages = AppSystemEnv.getPreInstallPackages();
        for (String preInstallPackage : preInstallPackages) {
            try {
                if (!PackageManagerService.get().isInstalled(preInstallPackage, BUserHandle.USER_ALL)) {
                    PackageInfo packageInfo = PrisonCore.getContext().getPackageManager().getPackageInfo(preInstallPackage, 0);
                    PackageManagerService.get().installPackageAsUser(packageInfo.applicationInfo.sourceDir, InstallOption.installBySystem(), BUserHandle.USER_ALL);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        // Initialize JAR environment using improved JarManager
        JarManager.getInstance().initializeAsync();
    }
}
