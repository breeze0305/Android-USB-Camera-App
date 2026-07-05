package com.jiangdg.ausbc.utils

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import javax.microedition.khronos.opengles.GL10

object OpenGLUtils {
    private const val TAG = "OpenGLUtils"

    fun isGlEsSupported(context: Context): Boolean {
        return (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let {
            try {
                it.deviceConfigurationInfo.glEsVersion.toFloat() >= 2.0f
            } catch (e: NumberFormatException) {
                Logger.w(TAG, "parse OpenGL ES version failed", e)
                false
            }
        } ?: false
    }

    private fun glGetErrorStr(err: Int): String {
        return when (err) {
            GL10.GL_STACK_OVERFLOW -> "stack overflow"
            GL10.GL_STACK_UNDERFLOW -> "stack underflow"
            GLES20.GL_NO_ERROR -> "GL_NO_ERROR"
            GLES20.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GLES20.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GLES20.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GLES20.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            GLES20.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            else -> "unknown"
        }
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Logger.e(TAG, "$op: glError 0x${Integer.toHexString(error)}:${glGetErrorStr(error)}")
        }
    }
}
