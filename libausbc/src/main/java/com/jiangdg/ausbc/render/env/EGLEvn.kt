package com.jiangdg.ausbc.render.env

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import com.jiangdg.ausbc.utils.Logger

class EGLEvn {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var windowSurface: Surface? = null
    private var config: EGLConfig? = null

    fun initEgl(curContext: EGLContext? = null): Boolean {
        releaseElg()

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            return logEglFailure("Get display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            return logEglFailure("Initialize display")
        }

        val selectedConfig = chooseConfig() ?: return logEglFailure("Choose config")
        config = selectedConfig

        val attributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        context = EGL14.eglCreateContext(
            display,
            selectedConfig,
            curContext ?: EGL14.EGL_NO_CONTEXT,
            attributes,
            0
        )
        if (context == EGL14.EGL_NO_CONTEXT) {
            return logEglFailure("Create context")
        }

        if (!EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) {
            return logEglFailure("Make context current")
        }

        Logger.i(TAG, "init EGL success")
        return true
    }

    fun setupSurface(surface: Surface?, surfaceWidth: Int = 0, surfaceHeight: Int = 0) {
        if (display == EGL14.EGL_NO_DISPLAY) return

        destroySurface()
        val selectedConfig = config ?: return
        val newSurface = if (surface == null) {
            val attributes = intArrayOf(
                EGL14.EGL_WIDTH, surfaceWidth,
                EGL14.EGL_HEIGHT, surfaceHeight,
                EGL14.EGL_NONE
            )
            EGL14.eglCreatePbufferSurface(display, selectedConfig, attributes, 0)
        } else {
            EGL14.eglCreateWindowSurface(display, selectedConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        }

        if (newSurface == EGL14.EGL_NO_SURFACE) {
            logEglFailure("Create surface")
            return
        }

        this.surface = newSurface
        windowSurface = surface
        Logger.i(TAG, "setup EGL surface success")
    }

    fun eglMakeCurrent() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (context == EGL14.EGL_NO_CONTEXT) return
        if (surface == EGL14.EGL_NO_SURFACE) return

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            logEglFailure("Make surface current")
        }
    }

    fun setPresentationTime(nanoseconds: Long) {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (surface == EGL14.EGL_NO_SURFACE) return
        if (windowSurface == null) return

        if (!EGLExt.eglPresentationTimeANDROID(display, surface, nanoseconds)) {
            logEglFailure("Set presentation time")
        }
    }

    fun swapBuffers() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (surface == EGL14.EGL_NO_SURFACE) return

        if (!EGL14.eglSwapBuffers(display, surface)) {
            logEglFailure("Swap buffers")
        }
    }

    fun releaseElg() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            destroySurface()

            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }

            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        } else {
            windowSurface?.release()
            windowSurface = null
        }

        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
        config = null
        Logger.i(TAG, "release EGL success")
    }

    fun getEGLContext(): EGLContext = EGL14.eglGetCurrentContext()

    private fun chooseConfig(): EGLConfig? {
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        val success = EGL14.eglChooseConfig(
            display,
            attributes,
            0,
            configs,
            0,
            configs.size,
            configCount,
            0
        )
        if (!success || configCount[0] == 0) return null
        return configs[0]
    }

    private fun destroySurface() {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, surface)
            surface = EGL14.EGL_NO_SURFACE
        }
        windowSurface?.release()
        windowSurface = null
    }

    private fun logEglFailure(action: String): Boolean {
        Logger.e(TAG, "$action failed. error = ${EGL14.eglGetError()}")
        return false
    }

    companion object {
        private const val TAG = "EGLEvn"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
