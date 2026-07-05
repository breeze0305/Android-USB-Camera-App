package com.jiangdg.ausbc.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.view.Surface
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.render.internal.CameraRender
import com.jiangdg.ausbc.render.internal.ScreenRender
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.SettableFuture
import com.jiangdg.ausbc.utils.Utils
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RenderManager(
    context: Context,
    private val previewWidth: Int,
    private val previewHeight: Int
) : SurfaceTexture.OnFrameAvailableListener, Handler.Callback {
    private var externalOesTextureId: Int? = null
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private val cameraRender = CameraRender(context)
    private val screenRender = ScreenRender(context)
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private val transformMatrix = FloatArray(16)
    private var surfaceTextureFuture: SettableFuture<SurfaceTexture>? = null

    init {
        Logger.i(TAG, "create RenderManager, Open ES version is ${Utils.getGLESVersion(context)}")
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_GL_INIT -> {
                val args = msg.obj as RenderSurface
                initializeGl(args.width, args.height, args.surface)
            }
            MSG_GL_DRAW -> drawFrame()
            MSG_GL_RELEASE -> releaseGl()
            MSG_GL_CHANGED_SIZE -> {
                val size = msg.obj as RenderSize
                applyRenderSize(size.width, size.height)
            }
            MSG_GL_ROUTE_ANGLE -> (msg.obj as? RotateType)?.let(cameraRender::setRotateAngle)
        }
        return true
    }

    fun startRenderScreen(
        w: Int,
        h: Int,
        outSurface: Surface?,
        listener: CameraSurfaceTextureListener? = null
    ) {
        val thread = HandlerThread(RENDER_THREAD).apply { start() }
        renderThread = thread
        renderHandler = Handler(thread.looper, this)
        surfaceTextureFuture = SettableFuture()
        renderHandler?.obtainMessage(MSG_GL_INIT, RenderSurface(w, h, outSurface))?.sendToTarget()

        val surfaceTexture = waitForCameraSurfaceTexture()
        surfaceTexture?.apply {
            setDefaultBufferSize(w, h)
            setOnFrameAvailableListener(this@RenderManager)
            cameraSurfaceTexture = this
        }
        listener?.onSurfaceTextureAvailable(surfaceTexture)
        Logger.i(TAG, "create camera SurfaceTexture: $surfaceTexture")
        setRenderSize(w, h)
    }

    fun stopRenderScreen() {
        renderHandler?.obtainMessage(MSG_GL_RELEASE)?.sendToTarget()
        renderThread?.quitSafely()
        renderThread = null
        renderHandler = null
        surfaceTextureFuture = null
    }

    fun setRenderSize(w: Int, h: Int) {
        renderHandler?.obtainMessage(MSG_GL_CHANGED_SIZE, RenderSize(w, h))?.sendToTarget()
    }

    fun setRotateType(type: RotateType?) {
        renderHandler?.obtainMessage(MSG_GL_ROUTE_ANGLE, type)?.sendToTarget()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        renderHandler?.obtainMessage(MSG_GL_DRAW)?.sendToTarget()
    }

    private fun initializeGl(width: Int, height: Int, surface: Surface?) {
        screenRender.initEGLEvn()
        screenRender.setupSurface(surface, width, height)
        screenRender.initGLES()
        cameraRender.initGLES()
        externalOesTextureId = cameraRender.getCameraTextureId()?.also { textureId ->
            surfaceTextureFuture?.set(SurfaceTexture(textureId))
        }
    }

    private fun applyRenderSize(width: Int, height: Int) {
        cameraRender.setSize(width, height)
        screenRender.setSize(width, height)
        cameraSurfaceTexture?.setDefaultBufferSize(width, height)
    }

    private fun drawFrame() {
        val surfaceTexture = cameraSurfaceTexture ?: return
        val oesTextureId = externalOesTextureId ?: return
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(transformMatrix)
        cameraRender.setTransformMatrix(transformMatrix)
        val frameTextureId = cameraRender.drawFrame(oesTextureId)
        screenRender.drawFrame(frameTextureId)
        screenRender.swapBuffers(surfaceTexture.timestamp)
    }

    private fun releaseGl() {
        cameraRender.releaseGLES()
        screenRender.releaseGLES()
        cameraSurfaceTexture?.setOnFrameAvailableListener(null)
        cameraSurfaceTexture = null
    }

    private fun waitForCameraSurfaceTexture(): SurfaceTexture? {
        return try {
            surfaceTextureFuture?.get(3, TimeUnit.SECONDS)
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
        }
    }

    interface CameraSurfaceTextureListener {
        fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?)
    }

    private data class RenderSurface(
        val width: Int,
        val height: Int,
        val surface: Surface?
    )

    private data class RenderSize(
        val width: Int,
        val height: Int
    )

    companion object {
        private const val TAG = "RenderManager"
        private const val RENDER_THREAD = "gl_render"
        private const val MSG_GL_INIT = 0x00
        private const val MSG_GL_DRAW = 0x01
        private const val MSG_GL_RELEASE = 0x02
        private const val MSG_GL_CHANGED_SIZE = 0x05
        private const val MSG_GL_ROUTE_ANGLE = 0x09
    }
}
