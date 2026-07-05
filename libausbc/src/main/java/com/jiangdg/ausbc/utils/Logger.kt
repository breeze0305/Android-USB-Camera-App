package com.jiangdg.ausbc.utils

import android.app.Application
import com.jiangdg.utils.XLogWrapper

object Logger {
    fun init(application: Application, folderPath: String? = null) =
        XLogWrapper.init(application, folderPath)

    fun i(flag: String, msg: String) = XLogWrapper.i(flag, msg)

    fun d(flag: String, msg: String) = XLogWrapper.d(flag, msg)

    fun w(flag: String, msg: String) = XLogWrapper.w(flag, msg)

    fun w(flag: String, throwable: Throwable?) = XLogWrapper.w(flag, throwable)

    fun w(flag: String, msg: String, throwable: Throwable?) = XLogWrapper.w(flag, msg, throwable)

    fun e(flag: String, msg: String) = XLogWrapper.e(flag, msg)

    fun e(flag: String, msg: String, throwable: Throwable?) = XLogWrapper.e(flag, msg, throwable)
}
