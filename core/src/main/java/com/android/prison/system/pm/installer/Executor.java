package com.android.prison.system.pm.installer;

import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.entity.InstallOption;

public interface Executor {
    public static final String TAG = Executor.class.getSimpleName();

    int exec(PPackageSettings ps, InstallOption option, int userId);
}
