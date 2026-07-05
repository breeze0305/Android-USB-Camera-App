package com.jiangdg.ausbc.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object Utils {

    var debugCamera = false

    fun isTargetSdkOverP(context: Context): Boolean {
        val targetSdkVersion = try {
            context.packageManager
                .getApplicationInfo(context.packageName, 0)
                .targetSdkVersion
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        return targetSdkVersion >= Build.VERSION_CODES.P
    }

    fun getGLESVersion(context: Context): String? {
        return (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.deviceConfigurationInfo
            ?.glEsVersion
    }
}
