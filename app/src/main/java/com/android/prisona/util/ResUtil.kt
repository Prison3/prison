package com.android.prisona.util

import androidx.annotation.StringRes
import com.android.prisona.FoxRiver

fun getString(@StringRes id:Int,vararg arg:String):String{
    if(arg.isEmpty()){
        return FoxRiver.getContext().getString(id)
    }
    return FoxRiver.getContext().getString(id,*arg)
}

