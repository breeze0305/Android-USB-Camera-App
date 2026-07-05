package com.jiangdg.ausbc

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.render.RenderManager
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.CameraUtils.isFilterDevice
import com.jiangdg.ausbc.utils.CameraUtils.isUsbCamera
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.OpenGLUtils
import com.jiangdg.ausbc.utils.SettableFuture
import com.jiangdg.ausbc.utils.Utils
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.usb.DeviceFilter
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.UVCCamera
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.abs

class MultiCameraClient(
    private val ctx: Context,
    private val callback: IDeviceConnectCallBack?
) {
    private var mUsbMonitor: USBMonitor? = null
    private val mMainHandler by lazy { Handler(Looper.getMainLooper()) }

    init {
        mUsbMonitor = USBMonitor(ctx, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                dispatchUsbDevice("attach", device) { callback?.onAttachDev(it) }
            }

            override fun onDetach(device: UsbDevice?) {
                dispatchUsbDevice("detach", device) { callback?.onDetachDec(it) }
            }

            override fun onConnect(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?,
                createNew: Boolean
            ) {
                dispatchUsbDevice("connect", device) {
                    callback?.onConnectDev(it, ctrlBlock)
                }
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                dispatchUsbDevice("disconnect", device) {
                    callback?.onDisConnectDec(it, ctrlBlock)
                }
            }

            override fun onCancel(device: UsbDevice?) {
                dispatchUsbDevice("cancel", device) { callback?.onCancelDev(it) }
            }
        })
    }

    private fun dispatchUsbDevice(
        event: String,
        device: UsbDevice?,
        action: (UsbDevice) -> Unit
    ) {
        if (Utils.debugCamera) {
            Logger.i(TAG, "$event device name/pid/vid:${device?.deviceName}&${device?.productId}&${device?.vendorId}")
        }
        if (device == null || (!isUsbCamera(device) && !isFilterDevice(ctx, device))) {
            return
        }
        mMainHandler.post {
            action(device)
        }
    }

    fun register() {
        if (isMonitorRegistered()) return
        if (Utils.debugCamera) {
            Logger.i(TAG, "register")
        }
        mUsbMonitor?.register()
    }

    fun unRegister() {
        if (!isMonitorRegistered()) return
        if (Utils.debugCamera) {
            Logger.i(TAG, "unregister")
        }
        mUsbMonitor?.unregister()
    }

    fun requestPermission(device: UsbDevice?): Boolean {
        if (!isMonitorRegistered()) {
            Logger.w(TAG, "USB monitor is not registered")
            return false
        }
        if (device == null) {
            Logger.w(TAG, "ignore permission request for null USB device")
            return false
        }
        mUsbMonitor?.requestPermission(device)
        return true
    }

    fun hasPermission(device: UsbDevice?) = mUsbMonitor?.hasPermission(device)

    fun getDeviceList(list: List<DeviceFilter>? = null): MutableList<UsbDevice>? {
        list?.let { addDeviceFilters(it) }
        return mUsbMonitor?.deviceList
    }

    fun addDeviceFilters(list: List<DeviceFilter>) {
        mUsbMonitor?.addDeviceFilter(list)
    }

    fun removeDeviceFilters(list: List<DeviceFilter>) {
        mUsbMonitor?.removeDeviceFilter(list)
    }

    fun destroy() {
        mUsbMonitor?.destroy()
    }

    fun openDebug(debug: Boolean) {
        Utils.debugCamera = debug
        USBMonitor.DEBUG = debug
        UVCCamera.DEBUG = debug
    }

    private fun isMonitorRegistered() = mUsbMonitor?.isRegistered == true

    abstract class ICamera(val ctx: Context, val device: UsbDevice) : Handler.Callback {
        private var mCameraThread: HandlerThread? = null
        private var mRenderManager: RenderManager? = null
        private var mCameraView: Any? = null
        private var mCameraStateCallback: ICameraStateCallBack? = null
        private var mSizeChangedFuture: SettableFuture<Pair<Int, Int>>? = null
        protected var mContext = ctx
        protected var mCameraRequest: CameraRequest? = null
        protected var mCameraHandler: Handler? = null
        protected var isPreviewed: Boolean = false
        protected var isNeedGLESRender: Boolean = false
        protected var mCtrlBlock: USBMonitor.UsbControlBlock? = null
        protected val mMainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                MSG_START_PREVIEW -> startPreview()
                MSG_STOP_PREVIEW -> stopPreview()
            }
            return true
        }

        protected abstract fun <T> openCameraInternal(cameraView: T)
        protected abstract fun closeCameraInternal()

        private fun startPreview() {
            val request = mCameraRequest ?: getDefaultCameraRequest().also { mCameraRequest = it }
            val previewWidth = request.previewWidth
            val previewHeight = request.previewHeight
            val previewView = mCameraView as? IAspectRatio

            if (request.isAspectRatioShow) {
                previewView?.setAspectRatio(previewWidth, previewHeight)
            }

            isNeedGLESRender = OpenGLUtils.isGlEsSupported(ctx)
            if (!isNeedGLESRender && previewView != null) {
                openCameraInternal(previewView)
                return
            }

            startOpenGlPreview(request, previewView, previewWidth, previewHeight)
        }

        private fun startOpenGlPreview(
            request: CameraRequest,
            previewView: IAspectRatio?,
            previewWidth: Int,
            previewHeight: Int
        ) {
            waitForSurfaceSize()
            val screenWidth = previewView?.getSurfaceWidth() ?: previewWidth
            val screenHeight = previewView?.getSurfaceHeight() ?: previewHeight
            val surface = previewView?.getSurface()
            mRenderManager = RenderManager(ctx, previewWidth, previewHeight)
            mRenderManager?.startRenderScreen(
                screenWidth,
                screenHeight,
                surface,
                object : RenderManager.CameraSurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?) {
                        if (surfaceTexture == null) {
                            closeCamera()
                            postStateEvent(ICameraStateCallBack.State.ERROR, "create camera surface failed")
                            return
                        }
                        openCameraInternal(surfaceTexture)
                    }
                }
            )
            mRenderManager?.setRotateType(request.defaultRotateType)
        }

        private fun waitForSurfaceSize(): Pair<Int, Int>? {
            val future = SettableFuture<Pair<Int, Int>>()
            mSizeChangedFuture = future
            val result = try {
                future.get(2000, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                if (Utils.debugCamera) {
                    Logger.i(TAG, "surface measure timed out; using current view size")
                }
                null
            } catch (e: CancellationException) {
                Logger.w(TAG, "surface measure cancelled", e)
                null
            } catch (e: ExecutionException) {
                Logger.e(TAG, "surface measure failed", e)
                null
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Logger.w(TAG, "surface measure interrupted", e)
                null
            }
            if (Utils.debugCamera) {
                Logger.i(TAG, "surface measure size $result")
            }
            return result
        }

        private fun stopPreview() {
            cancelSizeWait()
            closeCameraInternal()
            mRenderManager?.stopRenderScreen()
            mRenderManager = null
        }

        fun setRotateType(type: RotateType?) {
            mRenderManager?.setRotateType(type)
        }

        fun setRenderSize(width: Int, height: Int) {
            mSizeChangedFuture?.set(Pair(width, height))
            mRenderManager?.setRenderSize(width, height)
        }

        protected fun postStateEvent(state: ICameraStateCallBack.State, msg: String? = null) {
            mMainHandler.post {
                mCameraStateCallback?.onCameraState(this, state, msg)
            }
        }

        fun setUsbControlBlock(ctrlBlock: USBMonitor.UsbControlBlock?) {
            mCtrlBlock = ctrlBlock
        }

        fun getUsbDevice() = device

        abstract fun getAllPreviewSizes(aspectRatio: Double? = null): MutableList<PreviewSize>

        fun <T> openCamera(cameraView: T? = null, cameraRequest: CameraRequest? = null) {
            mCameraView = cameraView ?: mCameraView
            mCameraRequest = cameraRequest ?: getDefaultCameraRequest()
            val thread = HandlerThread("camera-${System.currentTimeMillis()}").apply { start() }
            mCameraThread = thread
            mCameraHandler = Handler(thread.looper, this)
            mCameraHandler?.obtainMessage(MSG_START_PREVIEW)?.sendToTarget()
        }

        fun closeCamera() {
            mCameraHandler?.obtainMessage(MSG_STOP_PREVIEW)?.sendToTarget()
            mCameraThread?.quitSafely()
            mCameraThread = null
            mCameraHandler = null
        }

        fun isCameraOpened() = isPreviewed

        fun getCameraRequest() = mCameraRequest

        fun updateResolution(width: Int, height: Int) {
            val request = mCameraRequest
            if (request == null) {
                Logger.w(TAG, "updateResolution failed, please open camera first.")
                return
            }
            if (request.previewWidth == width && request.previewHeight == height) {
                return
            }
            Logger.i(TAG, "updateResolution: width = $width, height = $height")
            closeCamera()
            mMainHandler.postDelayed({
                request.previewWidth = width
                request.previewHeight = height
                openCamera(mCameraView, request)
            }, 1000)
        }

        fun setCameraStateCallBack(callback: ICameraStateCallBack?) {
            mCameraStateCallback = callback
        }

        fun getSuitableSize(maxWidth: Int, maxHeight: Int): PreviewSize {
            val sizeList = getAllPreviewSizes()
            if (sizeList.isNullOrEmpty()) {
                return PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT)
            }

            sizeList.firstOrNull { it.width == maxWidth && it.height == maxHeight }?.let {
                return it
            }

            val aspectRatio = maxWidth.toFloat() / maxHeight
            sizeList.firstOrNull { size ->
                val ratio = size.width.toFloat() / size.height
                ratio == aspectRatio && size.width <= maxWidth && size.height <= maxHeight
            }?.let {
                return it
            }

            sizeList.firstOrNull {
                it.width == DEFAULT_PREVIEW_WIDTH || it.height == DEFAULT_PREVIEW_HEIGHT
            }?.let {
                return it
            }

            return sizeList.minByOrNull { abs(maxWidth - it.width) } ?: sizeList[0]
        }

        fun isPreviewSizeSupported(previewSize: PreviewSize): Boolean {
            return getAllPreviewSizes().any {
                it.width == previewSize.width && it.height == previewSize.height
            }
        }

        private fun getDefaultCameraRequest(): CameraRequest {
            return CameraRequest.Builder()
                .setPreviewWidth(1280)
                .setPreviewHeight(720)
                .create()
        }

        private fun cancelSizeWait() {
            try {
                mSizeChangedFuture?.cancel(true)
            } catch (e: RuntimeException) {
                Logger.w(TAG, "cancel surface measure wait failed", e)
            } finally {
                mSizeChangedFuture = null
            }
        }
    }

    companion object {
        private const val TAG = "MultiCameraClient"
        private const val MSG_START_PREVIEW = 0x01
        private const val MSG_STOP_PREVIEW = 0x02
        private const val DEFAULT_PREVIEW_WIDTH = 640
        private const val DEFAULT_PREVIEW_HEIGHT = 480
    }
}
