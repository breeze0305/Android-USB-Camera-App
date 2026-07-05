package com.jiangdg.ausbc.widget

import android.view.Surface

interface IAspectRatio {
    fun setAspectRatio(width: Int, height: Int)
    fun getSurfaceWidth(): Int
    fun getSurfaceHeight(): Int
    fun getSurface(): Surface?
    fun postUITask(task: () -> Unit)
}
