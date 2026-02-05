// IPPackageInstallerService.aidl
package com.android.prison.system.pm;

import com.android.prison.system.pm.PPackageSettings;
import com.android.prison.entity.InstallOption;

// Declare any non-default types here with import statements

interface IPPackageInstallerService {
    int installPackageAsUser(in PPackageSettings ps, int userId);
    int uninstallPackageAsUser(in PPackageSettings ps, boolean removeApp, int userId);
    int clearPackage(in PPackageSettings ps, int userId);
    int updatePackage(in PPackageSettings ps);
}
