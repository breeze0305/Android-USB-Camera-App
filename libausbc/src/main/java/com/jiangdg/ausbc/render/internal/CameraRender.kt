package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.render.env.RotateType

class CameraRender(context: Context) : AbstractFboRender(context) {
    private val surfaceTextureMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private var surfaceTextureMatrixHandle = -1
    private var mvpMatrixHandle = -1
    private var oesTextureId = -1

    override fun init() {
        oesTextureId = createOESTexture()
        Matrix.setIdentityM(surfaceTextureMatrix, 0)
        applyRotation(null)
        surfaceTextureMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uStMatrix")
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
    }

    override fun beforeDraw() {
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(surfaceTextureMatrixHandle, 1, false, surfaceTextureMatrix, 0)
    }

    override fun getBindTextureType(): Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    override fun getVertexSourceId(): Int = R.raw.camera_vertex

    override fun getFragmentSourceId(): Int = R.raw.camera_fragment

    fun setRotateAngle(type: RotateType) {
        applyRotation(type)
    }

    fun setTransformMatrix(matrix: FloatArray) {
        if (matrix.size >= surfaceTextureMatrix.size) {
            System.arraycopy(matrix, 0, surfaceTextureMatrix, 0, surfaceTextureMatrix.size)
        }
    }

    fun getCameraTextureId() = oesTextureId

    private fun applyRotation(type: RotateType?) {
        Matrix.setIdentityM(mvpMatrix, 0)
        when (type) {
            RotateType.ANGLE_0 -> Unit
            RotateType.ANGLE_90 -> Matrix.rotateM(mvpMatrix, 0, 90f, 0f, 0f, 1f)
            RotateType.ANGLE_180 -> Matrix.rotateM(mvpMatrix, 0, 180f, 0f, 0f, 1f)
            RotateType.ANGLE_270 -> Matrix.rotateM(mvpMatrix, 0, 270f, 0f, 0f, 1f)
            RotateType.FLIP_UP_DOWN -> Matrix.rotateM(mvpMatrix, 0, 180f, 1f, 0f, 0f)
            RotateType.FLIP_LEFT_RIGHT -> Matrix.rotateM(mvpMatrix, 0, 180f, 0f, 1f, 0f)
            null -> Unit
        }
    }
}
