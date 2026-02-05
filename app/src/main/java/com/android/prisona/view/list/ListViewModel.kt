package com.android.prisona.view.list

import androidx.lifecycle.MutableLiveData
import com.android.prisona.bean.InstalledAppBean
import com.android.prisona.data.AppsRepository
import com.android.prisona.view.base.BaseViewModel

class ListViewModel(private val repo: AppsRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<InstalledAppBean>>()

    val loadingLiveData = MutableLiveData<Boolean>()

    fun previewInstalledList() {
        launchOnUI{
            repo.previewInstallList()
        }
    }

    fun getInstallAppList(userID:Int){
        launchOnUI {
            repo.getInstalledAppList(userID,loadingLiveData,appsLiveData)
        }
    }

}