package com.android.prison.system.pm.installer;

import com.android.prison.base.PEnvironment;
import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.entity.InstallOption;
import com.android.prison.utils.FileUtils;

public class RemoveAppExecutor implements Executor {
    @Override
    public int exec(PPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(PEnvironment.getAppDir(ps.pkg.packageName));
        return 0;
    }
}
