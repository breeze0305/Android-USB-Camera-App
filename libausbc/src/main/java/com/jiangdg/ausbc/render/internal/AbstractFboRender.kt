package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.opengl.GLES20
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.OpenGLUtils

abstract class AbstractFboRender(context: Context) : AbstractRender(context) {
    private val frameBufferIds = IntArray(1)
    private val frameTextureIds = IntArray(1)

    fun getFrameBufferId() = frameBufferIds[0]

    fun getFrameBufferTexture() = frameTextureIds[0]

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        recreateFrameBuffer(width, height)
    }

    override fun drawFrame(textureId: Int): Int {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferIds[0])
        super.drawFrame(textureId)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        afterDrawFBO()
        return frameTextureIds[0]
    }

    override fun clear() {
        releaseFrameBuffer()
    }

    protected open fun afterDrawFBO() = Unit

    private fun recreateFrameBuffer(width: Int, height: Int) {
        releaseFrameBuffer()

        GLES20.glGenFramebuffers(1, frameBufferIds, 0)
        createTexture(frameTextureIds)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureIds[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferIds[0])
        OpenGLUtils.checkGlError("glBindFramebuffer")
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            frameTextureIds[0],
            0
        )
        OpenGLUtils.checkGlError("glFramebufferTexture2D")

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        Logger.i(TAG, "created FBO texture=${frameTextureIds[0]}, buffer=${frameBufferIds[0]}")
    }

    private fun releaseFrameBuffer() {
        if (frameTextureIds[0] != 0) {
            GLES20.glDeleteTextures(1, frameTextureIds, 0)
            frameTextureIds[0] = 0
        }
        if (frameBufferIds[0] != 0) {
            GLES20.glDeleteFramebuffers(1, frameBufferIds, 0)
            frameBufferIds[0] = 0
        }
    }

    companion object {
        private const val TAG = "AbstractFboRender"
    }
}
