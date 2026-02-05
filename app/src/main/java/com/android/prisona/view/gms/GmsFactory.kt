package com.android.prisona.view.gms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.prisona.data.GmsRepository

class GmsFactory(private val repo:GmsRepository): ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GmsViewModel(repo) as T
    }
}