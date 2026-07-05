package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.view.Surface
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.render.env.EGLEvn

class ScreenRender(context: Context) : AbstractRender(context) {
    private var eglEnv: EGLEvn? = null

    fun initEGLEvn() {
        eglEnv?.releaseElg()
        eglEnv = EGLEvn().also { it.initEgl() }
    }

    fun setupSurface(surface: Surface?, surfaceWidth: Int = 0, surfaceHeight: Int = 0) {
        eglEnv?.run {
            setupSurface(surface, surfaceWidth, surfaceHeight)
            eglMakeCurrent()
        }
    }

    fun swapBuffers(timeStamp: Long) {
        eglEnv?.run {
            setPresentationTime(timeStamp)
            swapBuffers()
        }
    }

    fun getCurrentContext() = eglEnv?.getEGLContext()

    override fun clear() {
        eglEnv?.releaseElg()
        eglEnv = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}
