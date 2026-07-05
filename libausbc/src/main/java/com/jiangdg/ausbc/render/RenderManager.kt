/*
 * Copyright 2017-2023 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.*
import android.view.Surface
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.render.internal.*
import com.jiangdg.ausbc.utils.*
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Render manager
 *
 * @property surfaceWidth camera preview width
 * @property surfaceHeight camera preview height
 *
 * @param context context
 *
 * @author Created by jiangdg on 2021/12/28
 */
class RenderManager(
    context: Context,
    private val surfaceWidth: Int,         // render surface width
    private val surfaceHeight: Int         // render surface height
) : SurfaceTexture.OnFrameAvailableListener, Handler.Callback {
    private var mEOSTextureId: Int? = null
    private var mRenderThread: HandlerThread? = null
    private var mRenderHandler: Handler? = null
    private var mCameraRender: CameraRender? = null
    private var mScreenRender: ScreenRender? = null
    private var mCameraSurfaceTexture: SurfaceTexture? = null
    private var mTransformMatrix: FloatArray = FloatArray(16)
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mContext: Context = context
    private val mStFuture by lazy {
        SettableFuture<SurfaceTexture>()
    }
    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }
    init {
        this.mCameraRender = CameraRender(context)
        this.mScreenRender = ScreenRender(context)
        Logger.i(TAG, "create RenderManager, Open ES version is ${Utils.getGLESVersion(context)}")
    }

    /**
     * Rendering processing logic
     *
     * Note: EGL must be initialized first, otherwise GL cannot run
     */
    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_GL_INIT -> {
                (msg.obj as Triple<*, *, *>).apply {
                    val w = first as Int
                    val h = second as Int
                    val surface = third as? Surface
                    mScreenRender?.initEGLEvn()
                    mScreenRender?.setupSurface(surface, w, h)
                    mScreenRender?.initGLES()
                    mCameraRender?.initGLES()
                    mEOSTextureId = mCameraRender?.getCameraTextureId()?.apply {
                        mStFuture.set(SurfaceTexture(this))
                    }
                }
            }
            MSG_GL_CHANGED_SIZE -> {
                (msg.obj as Pair<*, *>).apply {
                    mWidth = first as Int
                    mHeight = second as Int
                    mCameraRender?.setSize(mWidth, mHeight)
                    mScreenRender?.setSize(mWidth, mHeight)
                    mCameraSurfaceTexture?.setDefaultBufferSize(mWidth, mHeight)
                }
            }
            MSG_GL_ROUTE_ANGLE -> {
                (msg.obj as? RotateType)?.apply {
                    mCameraRender?.setRotateAngle(this)
                }
            }
            MSG_GL_DRAW -> {
                //Render camera data to SurfaceTexture
                //Set the correction matrix of the image at the same time
                mCameraSurfaceTexture?.updateTexImage()
                mCameraSurfaceTexture?.getTransformMatrix(mTransformMatrix)
                mCameraRender?.setTransformMatrix(mTransformMatrix)
                val textureId = mEOSTextureId?.let { mCameraRender?.drawFrame(it) }
                textureId?.also { id ->
                    mScreenRender?.drawFrame(id)
                }
                mScreenRender?.swapBuffers(mCameraSurfaceTexture?.timestamp ?: 0)
            }
            MSG_GL_RELEASE -> {
                mCameraRender?.releaseGLES()
                mScreenRender?.releaseGLES()
                mCameraSurfaceTexture?.setOnFrameAvailableListener(null)
                mCameraSurfaceTexture = null
            }
        }
        return true
    }

    /**
     * Start render screen
     *
     * @param w surface width
     * @param h surface height
     * @param outSurface render surface
     * @param listener acquire camera surface texture, see [CameraSurfaceTextureListener]
     */
    fun startRenderScreen(w: Int, h: Int, outSurface: Surface?, listener: CameraSurfaceTextureListener? = null) {
        mRenderThread = HandlerThread(RENDER_THREAD)
        mRenderThread?.start()
        mRenderHandler = Handler(mRenderThread!!.looper, this@RenderManager)
        Triple(w, h, outSurface).apply {
            mRenderHandler?.obtainMessage(MSG_GL_INIT, this)?.sendToTarget()
        }
        // wait camera SurfaceTexture created
        try {
            mStFuture.get(3, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Logger.e(TAG, "wait for creating camera SurfaceTexture timed out", e)
            null
        } catch (e: CancellationException) {
            Logger.e(TAG, "wait for creating camera SurfaceTexture cancelled", e)
            null
        } catch (e: ExecutionException) {
            Logger.e(TAG, "wait for creating camera SurfaceTexture failed", e)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Logger.e(TAG, "wait for creating camera SurfaceTexture interrupted", e)
            null
        }?.apply {
            setDefaultBufferSize(w, h)
            setOnFrameAvailableListener(this@RenderManager)
            mCameraSurfaceTexture = this
        }.also {
            listener?.onSurfaceTextureAvailable(it)
            Logger.i(TAG, "create camera SurfaceTexture: $it")
        }
        setRenderSize(w, h)
    }

    /**
     * Stop render screen
     */
    fun stopRenderScreen() {
        mRenderHandler?.obtainMessage(MSG_GL_RELEASE)?.sendToTarget()
        mRenderThread?.quitSafely()
        mRenderThread = null
        mRenderHandler = null
    }

    /**
     * Set render size
     *
     * @param w surface width
     * @param h surface height
     */
    fun setRenderSize(w: Int, h: Int) {
        mRenderHandler?.obtainMessage(MSG_GL_CHANGED_SIZE, Pair(w, h))?.sendToTarget()
    }

    /**
     * Rotate camera render angle
     *
     * @param type rotate angle, null means rotating nothing
     * see [RotateType.ANGLE_90], [RotateType.ANGLE_270],...etc.
     */
    fun setRotateType(type: RotateType?) {
        mRenderHandler?.obtainMessage(MSG_GL_ROUTE_ANGLE, type)?.sendToTarget()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        mRenderHandler?.obtainMessage(MSG_GL_DRAW)?.sendToTarget()
    }

    /**
     * Camera surface texture listener
     *
     * @constructor Create empty Camera surface texture listener
     */
    interface CameraSurfaceTextureListener {
        /**
         * On surface texture available
         *
         * @param surfaceTexture camera render surface texture
         */
        fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?)
    }

    companion object {
        private const val TAG = "RenderManager"
        private const val RENDER_THREAD = "gl_render"
        // render
        private const val MSG_GL_INIT = 0x00
        private const val MSG_GL_DRAW = 0x01
        private const val MSG_GL_RELEASE = 0x02
        private const val MSG_GL_CHANGED_SIZE = 0x05
        private const val MSG_GL_ROUTE_ANGLE = 0x09
    }
}
