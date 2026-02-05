package com.android.prisona.util

import com.android.prisona.data.AppsRepository
import com.android.prisona.data.GmsRepository
import com.android.prisona.view.apps.AppsFactory
import com.android.prisona.view.gms.GmsFactory
import com.android.prisona.view.list.ListFactory

object InjectionUtil {

    private val appsRepository = AppsRepository()

    private val gmsRepository = GmsRepository()

    fun getAppsFactory() : AppsFactory {
        return AppsFactory(appsRepository)
    }

    fun getListFactory(): ListFactory {
        return ListFactory(appsRepository)
    }

    fun getGmsFactory():GmsFactory{
        return GmsFactory(gmsRepository)
    }
}