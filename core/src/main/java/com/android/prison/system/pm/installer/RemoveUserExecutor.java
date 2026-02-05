package com.android.prison.system.pm.installer;

import com.android.prison.base.PEnvironment;
import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.entity.InstallOption;
import com.android.prison.utils.FileUtils;

public class RemoveUserExecutor implements Executor {

    @Override
    public int exec(PPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        // delete user dir
        FileUtils.deleteDir(PEnvironment.getDataDir(packageName, userId));
        FileUtils.deleteDir(PEnvironment.getDeDataDir(packageName, userId));
        FileUtils.deleteDir(PEnvironment.getExternalDataDir(packageName, userId));
        return 0;
    }
}
