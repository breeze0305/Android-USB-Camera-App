package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.MediaUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

abstract class AbstractRender(private val context: Context) {
    private var vertexShader = 0
    private var fragmentShader = 0
    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    protected var mWidth: Int = 0
    protected var mHeight: Int = 0
    protected var mProgram: Int = 0
    protected var mPositionLocation = -1
    protected var mTextureCoordLocation = -1
    protected var mTextureSampler = -1

    open fun setSize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        GLES20.glViewport(0, 0, mWidth, mHeight)
    }

    open fun drawFrame(textureId: Int): Int {
        if (mProgram == 0) return textureId

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glViewport(0, 0, mWidth, mHeight)
        GLES20.glUseProgram(mProgram)

        vertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            mPositionLocation,
            POSITION_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            vertices
        )
        GLES20.glEnableVertexAttribArray(mPositionLocation)

        vertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            mTextureCoordLocation,
            UV_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            vertices
        )
        GLES20.glEnableVertexAttribArray(mTextureCoordLocation)

        beforeDraw()

        val textureTarget = getBindTextureType()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glUniform1i(mTextureSampler, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glDisableVertexAttribArray(mPositionLocation)
        GLES20.glDisableVertexAttribArray(mTextureCoordLocation)
        return textureId
    }

    fun initGLES() {
        val vertexShaderSource = MediaUtils.readRawTextFile(context, getVertexSourceId())
        val fragmentShaderSource = MediaUtils.readRawTextFile(context, getFragmentSourceId())
        mProgram = createProgram(vertexShaderSource, fragmentShaderSource)
        if (mProgram == 0) {
            Logger.e(TAG, "create program failed, err = ${GLES20.glGetError()}")
            return
        }

        mPositionLocation = GLES20.glGetAttribLocation(mProgram, "aPosition")
        mTextureCoordLocation = GLES20.glGetAttribLocation(mProgram, "aTextureCoordinate")
        mTextureSampler = GLES20.glGetUniformLocation(mProgram, "uTextureSampler")
        if (mPositionLocation < 0 || mTextureCoordLocation < 0 || mTextureSampler < 0) {
            Logger.e(
                TAG,
                "shader handle lookup failed, pos=$mPositionLocation uv=$mTextureCoordLocation sampler=$mTextureSampler"
            )
            return
        }

        init()
        Logger.i(TAG, "init GLES render success")
    }

    fun releaseGLES() {
        if (vertexShader != 0) {
            GLES20.glDeleteShader(vertexShader)
            vertexShader = 0
        }
        if (fragmentShader != 0) {
            GLES20.glDeleteShader(fragmentShader)
            fragmentShader = 0
        }
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram)
            mProgram = 0
        }
        clear()
        Logger.i(TAG, "release GLES render success")
    }

    fun getRenderWidth() = mWidth

    fun getRenderHeight() = mHeight

    protected open fun beforeDraw() = Unit

    protected open fun init() = Unit

    protected open fun clear() = Unit

    protected open fun getBindTextureType() = GLES20.GL_TEXTURE_2D

    protected abstract fun getVertexSourceId(): Int

    protected abstract fun getFragmentSourceId(): Int

    protected fun createTexture(textures: IntArray) {
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        applyTextureParameters(GLES20.GL_TEXTURE_2D)
        Logger.i(TAG, "create texture, id=${textures[0]}")
    }

    fun createOESTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        applyTextureParameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        Logger.i(TAG, "create external texture, id=${textures[0]}")
        return textures[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Logger.e(TAG, "shader program link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != GLES20.GL_TRUE) {
            Logger.e(TAG, "shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun applyTextureParameters(textureTarget: Int) {
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    companion object {
        private const val TAG = "AbstractRender"
        private const val FLOAT_SIZE_BYTES = 4
        private const val POSITION_COMPONENT_COUNT = 3
        private const val UV_COMPONENT_COUNT = 2
        private const val VERTEX_COUNT = 4

        const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        const val TRIANGLE_VERTICES_DATA_UV_OFFSET = POSITION_COMPONENT_COUNT

        private val FULLSCREEN_QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            -1f, 1f, 0f, 0f, 1f,
            1f, 1f, 0f, 1f, 1f,
        )
    }
}
