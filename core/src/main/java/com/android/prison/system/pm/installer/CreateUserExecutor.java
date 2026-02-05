package com.android.prison.system.pm.installer;

import com.android.prison.base.PEnvironment;
import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.entity.InstallOption;
import com.android.prison.utils.FileUtils;

public class CreateUserExecutor implements Executor {

    @Override
    public int exec(PPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        FileUtils.deleteDir(PEnvironment.getDataLibDir(packageName, userId));

        // create user dir
        FileUtils.mkdirs(PEnvironment.getDataDir(packageName, userId));
        FileUtils.mkdirs(PEnvironment.getDataCacheDir(packageName, userId));
        FileUtils.mkdirs(PEnvironment.getDataFilesDir(packageName, userId));
        FileUtils.mkdirs(PEnvironment.getDataDatabasesDir(packageName, userId));
        FileUtils.mkdirs(PEnvironment.getDeDataDir(packageName, userId));

//        try {
//            // /data/data/xx/lib -> /data/app/xx/lib
//            FileUtils.createSymlink(PEnvironment.getAppLibDir(ps.pkg.packageName).getAbsolutePath(), PEnvironment.getDataLibDir(packageName, userId).getAbsolutePath());
//        } catch (Exception e) {
//            e.printStackTrace();
//            return -1;
//        }
        return 0;
    }
}
