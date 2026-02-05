package com.android.prisona.data

import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.android.prison.core.PrisonCore
import com.android.prison.entity.InstallOption
import com.android.prison.manager.PPackageManager
import com.android.prison.manager.PUserManager
import com.android.prison.utils.AbiUtils
import com.android.prisona.R
import com.android.prisona.FoxRiver
import com.android.prisona.bean.AppInfo
import com.android.prisona.bean.InstalledAppBean
import com.android.prisona.util.MemoryManager
import com.android.prisona.util.getString
import java.io.File
import android.webkit.URLUtil
import com.android.prison.utils.Logger

class AppsRepository {
    val TAG: String = "AppsRepository"
    private var mInstalledList = mutableListOf<AppInfo>()
    
    /**
     * Safely load app label with fallback to package name
     */
    private fun safeLoadAppLabel(applicationInfo: ApplicationInfo): String {
        return try {
            PrisonCore.getContext().getPackageManager().getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to load label for ${applicationInfo.packageName}: ${e.message}")
            applicationInfo.packageName // Fallback to package name
        }
    }
    
    /**
     * Safely load app icon with fallback to null
     */
    private fun safeLoadAppIcon(applicationInfo: ApplicationInfo): android.graphics.drawable.Drawable? {
        return try {
            // Check if we should skip icon loading to save memory
            if (MemoryManager.shouldSkipIconLoading()) {
                Logger.w(TAG, "Memory usage high (${MemoryManager.getMemoryUsagePercentage()}%), skipping icon for ${applicationInfo.packageName}")
                return null
            }
            
            val icon = PrisonCore.getContext().getPackageManager().getApplicationIcon(applicationInfo)
            
            // Optimize icon for memory efficiency
            if (icon is android.graphics.drawable.BitmapDrawable) {
                val bitmap = icon.bitmap
                // If icon is too large, scale it down to save memory
                if (bitmap.width > 96 || bitmap.height > 96) {
                    try {
                        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                        android.graphics.drawable.BitmapDrawable(PrisonCore.getContext().getPackageManager().getResourcesForApplication(applicationInfo.packageName), scaledBitmap)
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to scale icon for ${applicationInfo.packageName}: ${e.message}")
                        icon
                    }
                } else {
                    icon
                }
            } else {
                icon
            }
            
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to load icon for ${applicationInfo.packageName}: ${e.message}")
            null // Fallback to null icon
        }
    }

    fun previewInstallList() {
        try {
            synchronized(mInstalledList) {
                val installedApplications: List<ApplicationInfo> =
                    PrisonCore.getContext().getPackageManager().getInstalledApplications(0)
                val installedList = mutableListOf<AppInfo>()

                for (installedApplication in installedApplications) {
                    try {
                        val file = File(installedApplication.sourceDir)

                        if ((installedApplication.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

                        if (!AbiUtils.isSupport(file)) continue

                        // Filter out Prison apps to prevent cloning
                        if (PrisonCore.get().isPrisonApp(installedApplication.packageName)) {
                            Logger.d(TAG, "Filtering out Prison app: ${installedApplication.packageName}")
                            continue
                        }

                        val info = AppInfo(
                            safeLoadAppLabel(installedApplication),
                            safeLoadAppIcon(installedApplication), // Remove the !! operator to allow null icons
                            installedApplication.packageName,
                            installedApplication.sourceDir
                        )
                        installedList.add(info)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error processing app ${installedApplication.packageName}: ${e.message}")
                    }
                }
                this.mInstalledList.clear()
                this.mInstalledList.addAll(installedList)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error in previewInstallList: ${e.message}")
        }
    }

    fun getInstalledAppList(
        userID: Int,
        loadingLiveData: MutableLiveData<Boolean>,
        appsLiveData: MutableLiveData<List<InstalledAppBean>>
    ) {
        try {
            loadingLiveData.postValue(true)
            synchronized(mInstalledList) {
                val prisonCore = PrisonCore.get()
                Logger.d(TAG, mInstalledList.joinToString(","))
                val newInstalledList = mInstalledList.map {
                    InstalledAppBean(
                        it.name,
                        it.icon, // Remove the !! operator to allow null icons
                        it.packageName,
                        it.sourceDir,
                        PPackageManager.get().isInstalled(it.packageName, userID)
                    )
                }
                appsLiveData.postValue(newInstalledList)
                loadingLiveData.postValue(false)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error in getInstalledAppList: ${e.message}")
            loadingLiveData.postValue(false)
            appsLiveData.postValue(emptyList())
        }
    }


    fun getVmInstallList(userId: Int, appsLiveData: MutableLiveData<List<AppInfo>>) {
        try {
            // Check memory status before starting
            if (MemoryManager.isMemoryCritical()) {
                Logger.w(TAG, "Memory critical (${MemoryManager.getMemoryUsagePercentage()}%), forcing garbage collection")
                MemoryManager.forceGarbageCollectionIfNeeded()
            }
            
            val prisonCore = PrisonCore.get()
            
            // Add debugging for users
            val users = PUserManager.get().getUsers()
            Logger.d(TAG, "getVmInstallList: userId=$userId, total users=${users.size}")
            users.forEach { user ->
                Logger.d(TAG, "User: id=${user.id}, name=${user.name}")
            }
            
            val sortListData =
                FoxRiver.getRemarkSharedPreferences().getString("AppList$userId", "")
            val sortList = sortListData?.split(",")

            // Add retry mechanism for getting installed applications
            var applicationList: List<ApplicationInfo>? = null
            var retryCount = 0
            val maxRetries = 3
            
            while (applicationList == null && retryCount < maxRetries) {
                try {
                    applicationList = PPackageManager.get().getInstalledApplications(0, userId)
                    if (applicationList == null) {
                        Logger.w(TAG, "getVmInstallList: Attempt ${retryCount + 1} returned null, retrying...")
                        retryCount++
                        Thread.sleep(100) // Small delay before retry
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "getVmInstallList: Error getting applications on attempt ${retryCount + 1}: ${e.message}")
                    retryCount++
                    if (retryCount < maxRetries) {
                        Thread.sleep(200) // Longer delay for errors
                    }
                }
            }
            
            // Add null check for applicationList
            if (applicationList == null) {
                Logger.e(TAG, "getVmInstallList: applicationList is null for userId=$userId after $maxRetries attempts")
                appsLiveData.postValue(emptyList())
                return
            }
            
            // Add debugging
            Logger.d(TAG, "getVmInstallList: userId=$userId, applicationList.size=${applicationList.size}")
            if (applicationList.isNotEmpty()) {
                Logger.d(TAG, "First app: ${applicationList.first().packageName}")
            } else {
                Logger.w(TAG, "getVmInstallList: No applications found for userId=$userId")
            }

            val appInfoList = mutableListOf<AppInfo>()
            
            // Sort the application list if sort data exists
            val sortedApplicationList = if (!sortList.isNullOrEmpty()) {
                try {
                    applicationList.sortedWith(AppsSortComparator(sortList))
                } catch (e: Exception) {
                    Logger.e(TAG, "getVmInstallList: Error sorting applications: ${e.message}")
                    applicationList // Return unsorted list if sorting fails
                }
            } else {
                applicationList
            }
            
            // Process each application with enhanced error handling
            sortedApplicationList.forEachIndexed { index, applicationInfo ->
                try {
                    // Check memory periodically during processing
                    if (index > 0 && index % 25 == 0) {
                        if (MemoryManager.isMemoryCritical()) {
                            Logger.w(TAG, "Memory critical during processing, forcing GC")
                            MemoryManager.forceGarbageCollectionIfNeeded()
                        }
                    }
                    
                    // Add null check for applicationInfo
                    if (applicationInfo == null) {
                        Logger.w(TAG, "getVmInstallList: Skipping null applicationInfo at index $index")
                        return@forEachIndexed
                    }
                    
                    // Validate package name
                    if (applicationInfo.packageName.isNullOrBlank()) {
                        Logger.w(TAG, "getVmInstallList: Skipping app with null/blank package name at index $index")
                        return@forEachIndexed
                    }
                    
                    val info = AppInfo(
                        safeLoadAppLabel(applicationInfo),
                        safeLoadAppIcon(applicationInfo), // Remove the !! operator to allow null icons
                        applicationInfo.packageName,
                        applicationInfo.sourceDir ?: ""
                    )

                    appInfoList.add(info)
                    
                    // Log progress for large lists
                    if (index > 0 && index % 50 == 0) {
                        Logger.d(TAG, "getVmInstallList: Processed $index/${sortedApplicationList.size} apps - ${MemoryManager.getMemoryInfo()}")
                    }
                    
                } catch (e: Exception) {
                    Logger.e(TAG, "getVmInstallList: Error processing app at index $index (${applicationInfo?.packageName}): ${e.message}")
                    // Continue processing other apps instead of failing completely
                }
            }

            Logger.d(TAG, "getVmInstallList: processed ${appInfoList.size} apps - ${MemoryManager.getMemoryInfo()}")
            
            // If no virtual apps found, show empty list (correct behavior for new users)
            // Do NOT load regular installed apps as fallback - this causes the bug
            if (appInfoList.isEmpty()) {
                Logger.d(TAG, "getVmInstallList: No virtual apps found for userId=$userId, showing empty list (correct for new users)")
            } else {
                Logger.d(TAG, "getVmInstallList: Showing ${appInfoList.size} virtual apps for userId=$userId")
            }
            
            // Post the result safely
            try {
                appsLiveData.postValue(appInfoList)
            } catch (e: Exception) {
                Logger.e(TAG, "getVmInstallList: Error posting to LiveData: ${e.message}")
                // Try to post on main thread as fallback
                try {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            appsLiveData.postValue(appInfoList)
                        } catch (e2: Exception) {
                            Logger.e(TAG, "getVmInstallList: Fallback posting also failed: ${e2.message}")
                        }
                    }
                } catch (e3: Exception) {
                    Logger.e(TAG, "getVmInstallList: Could not schedule fallback posting: ${e3.message}")
                }
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Error in getVmInstallList: ${e.message}")
            try {
                appsLiveData.postValue(emptyList())
            } catch (e2: Exception) {
                Logger.e(TAG, "getVmInstallList: Error posting empty list: ${e2.message}")
            }
        }
    }


    fun installApk(source: String, userId: Int, resultLiveData: MutableLiveData<String>) {
        try {
            // Check if this is an attempt to install Prison app
            if (source.contains("prison") || source.contains("niunaijun") || 
                source.contains("vspace") || source.contains("virtual")) {
                // Additional check for the actual Prison app
                try {
                    val prisonCore = PrisonCore.get()
                    val hostPackageName = PrisonCore.getPackageName()
                    
                    // If it's a file path, try to check the package name
                    if (!URLUtil.isValidUrl(source)) {
                        val file = File(source)
                        if (file.exists()) {
                            val packageInfo = PrisonCore.getContext().getPackageManager().getPackageArchiveInfo(source, 0)
                            if (packageInfo != null && packageInfo.packageName == hostPackageName) {
                                resultLiveData.postValue("Cannot install Prison app from within Prison. This would create infinite recursion and is not allowed for security reasons.")
                                return
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Could not verify if this is Prison app: ${e.message}")
                }
            }
            
            val prisonCore = PrisonCore.get()
            val installResult = if (URLUtil.isValidUrl(source)) {
                val uri = Uri.parse(source)
                PPackageManager.get().installPackageAsUser(uri.toString(), InstallOption.installByStorage().makeUriFile(), userId)
            } else {
                PPackageManager.get().installPackageAsUser(source, userId)
            }

            if (installResult.success) {
                updateAppSortList(userId, installResult.packageName, true)
                resultLiveData.postValue(getString(R.string.install_success))
            } else {
                resultLiveData.postValue(getString(R.string.install_fail, installResult.msg))
            }
            scanUser()
        } catch (e: Exception) {
            Logger.e(TAG, "Error installing APK: ${e.message}")
            resultLiveData.postValue("Installation failed: ${e.message}")
        }
    }

    fun unInstall(packageName: String, userID: Int, resultLiveData: MutableLiveData<String>) {
        try {
            PPackageManager.get().uninstallPackageAsUser(packageName, userID)
            updateAppSortList(userID, packageName, false)
            scanUser()
            resultLiveData.postValue(getString(R.string.uninstall_success))
        } catch (e: Exception) {
            Logger.e(TAG, "Error uninstalling APK: ${e.message}")
            resultLiveData.postValue("Uninstallation failed: ${e.message}")
        }
    }

    fun launchApk(packageName: String, userId: Int, launchLiveData: MutableLiveData<Boolean>) {
        try {
            val result = PrisonCore.get().launchApk(packageName, userId)
            launchLiveData.postValue(result)
        } catch (e: Exception) {
            Logger.e(TAG, "Error launching APK: ${e.message}")
            launchLiveData.postValue(false)
        }
    }

    fun clearApkData(packageName: String, userID: Int, resultLiveData: MutableLiveData<String>) {
        try {
            PPackageManager.get().clearPackage(packageName, userID)
            resultLiveData.postValue(getString(R.string.clear_success))
        } catch (e: Exception) {
            Logger.e(TAG, "Error clearing APK data: ${e.message}")
            resultLiveData.postValue("Clear failed: ${e.message}")
        }
    }

    /**
     * 倒序递归扫描用户，
     * 如果用户是空的，就删除用户，删除用户备注，删除应用排序列表
     */
    private fun scanUser() {
        try {
            val prisonCore = PrisonCore.get()
            val userList = PUserManager.get().getUsers()

            if (userList.isEmpty()) {
                return
            }

            val id = userList.last().id

            if (PPackageManager.get().getInstalledApplications(0, id).isEmpty()) {
                PUserManager.get().deleteUser(id)
                FoxRiver.getRemarkSharedPreferences().edit().apply {
                    remove("Remark$id")
                    remove("AppList$id")
                    apply()
                }
                scanUser()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    /**
     * 更新排序列表
     * @param userID Int
     * @param pkg String
     * @param isAdd Boolean true是添加，false是移除
     */
    private fun updateAppSortList(userID: Int, pkg: String, isAdd: Boolean) {
        try {
            val savedSortList =
                FoxRiver.getRemarkSharedPreferences().getString("AppList$userID", "")

            val sortList = linkedSetOf<String>()
            if (savedSortList != null) {
                sortList.addAll(savedSortList.split(","))
            }

            if (isAdd) {
                sortList.add(pkg)
            } else {
                sortList.remove(pkg)
            }

            FoxRiver.getRemarkSharedPreferences().edit().apply {
                putString("AppList$userID", sortList.joinToString(","))
                apply()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error updating app sort list: ${e.message}")
        }
    }

    /**
     * 保存排序后的apk顺序
     */
    fun updateApkOrder(userID: Int, dataList: List<AppInfo>) {
        try {
            FoxRiver.getRemarkSharedPreferences().edit().apply {
                putString("AppList$userID",
                    dataList.joinToString(",") { it.packageName })
                apply()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error updating APK order: ${e.message}")
        }
    }
}
