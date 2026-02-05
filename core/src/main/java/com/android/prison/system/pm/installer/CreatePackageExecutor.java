package com.android.prison.system.pm.installer;

import com.android.prison.base.PEnvironment;
import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.entity.InstallOption;
import com.android.prison.utils.FileUtils;

public class CreatePackageExecutor implements Executor {

    @Override
    public int exec(PPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(PEnvironment.getAppDir(ps.pkg.packageName));

        // create app dir
        FileUtils.mkdirs(PEnvironment.getAppDir(ps.pkg.packageName));
        FileUtils.mkdirs(PEnvironment.getAppLibDir(ps.pkg.packageName));
        return 0;
    }
}
