package com.android.prisona.data

import androidx.lifecycle.MutableLiveData
import com.android.prison.manager.GmsManager
import com.android.prison.manager.PUserManager
import com.android.prisona.R
import com.android.prisona.FoxRiver
import com.android.prisona.bean.GmsBean
import com.android.prisona.bean.GmsInstallBean
import com.android.prisona.util.getString

class GmsRepository {

    fun getGmsInstalledList(mInstalledLiveData: MutableLiveData<List<GmsBean>>) {
        val userList = arrayListOf<GmsBean>()

        PUserManager.get().getUsers().forEach {
            val userId = it.id
            val userName =
                FoxRiver.getRemarkSharedPreferences().getString("Remark$userId", "User $userId") ?: ""
            val isInstalled = GmsManager.isInstalledGoogleService(userId)
            val bean = GmsBean(userId, userName, isInstalled)
            userList.add(bean)
        }

        mInstalledLiveData.postValue(userList)
    }

    fun installGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        val installResult = GmsManager.installGApps(userID)

        val result = if (installResult.success) {
            getString(R.string.install_success)
        } else {
            getString(R.string.install_fail, installResult.msg)
        }

        val bean = GmsInstallBean(userID,installResult.success,result)
        mUpdateInstalledLiveData.postValue(bean)
    }

    fun uninstallGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        var isSuccess = false
        if (GmsManager.isInstalledGoogleService(userID)) {
            GmsManager.uninstallGApps(userID)
            isSuccess = !GmsManager.isInstalledGoogleService(userID)
        }

        val result = if (isSuccess) {
            getString(R.string.uninstall_success)
        } else {
            getString(R.string.uninstall_fail)
        }

        val bean = GmsInstallBean(userID,isSuccess,result)

        mUpdateInstalledLiveData.postValue(bean)
    }
}