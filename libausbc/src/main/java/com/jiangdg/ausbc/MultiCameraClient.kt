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

class MultiCameraClient(ctx: Context, callback: IDeviceConnectCallBack?) {
    private var mUsbMonitor: USBMonitor? = null
    private val mMainHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    init {
        mUsbMonitor = USBMonitor(ctx, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                dispatchUsbDevice(ctx, "attach", device) { callback?.onAttachDev(it) }
            }

            override fun onDetach(device: UsbDevice?) {
                dispatchUsbDevice(ctx, "detach", device) { callback?.onDetachDec(it) }
            }

            override fun onConnect(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?,
                createNew: Boolean
            ) {
                dispatchUsbDevice(ctx, "connect", device) {
                    callback?.onConnectDev(it, ctrlBlock)
                }
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                dispatchUsbDevice(ctx, "disconnect", device) {
                    callback?.onDisConnectDec(it, ctrlBlock)
                }
            }

            override fun onCancel(device: UsbDevice?) {
                dispatchUsbDevice(ctx, "cancel", device) { callback?.onCancelDev(it) }
            }
        })
    }

    private fun dispatchUsbDevice(
        context: Context,
        event: String,
        device: UsbDevice?,
        action: (UsbDevice) -> Unit
    ) {
        if (Utils.debugCamera) {
            Logger.i(TAG, "$event device name/pid/vid:${device?.deviceName}&${device?.productId}&${device?.vendorId}")
        }
        if (device == null || (!isUsbCamera(device) && !isFilterDevice(context, device))) {
            return
        }
        mMainHandler.post {
            action(device)
        }
    }

    fun register() {
        if (isMonitorRegistered()) {
            return
        }
        if (Utils.debugCamera) {
            Logger.i(TAG, "register...")
        }
        mUsbMonitor?.register()
    }

    fun unRegister() {
        if (!isMonitorRegistered()) {
            return
        }
        if (Utils.debugCamera) {
            Logger.i(TAG, "unRegister...")
        }
        mUsbMonitor?.unregister()
    }

    fun requestPermission(device: UsbDevice?): Boolean {
        if (!isMonitorRegistered()) {
            Logger.w(TAG, "Usb monitor haven't been registered.")
            return false
        }
        mUsbMonitor?.requestPermission(device)
        return true
    }

    fun hasPermission(device: UsbDevice?) = mUsbMonitor?.hasPermission(device)

    fun getDeviceList(list: List<DeviceFilter>? = null): MutableList<UsbDevice>? {
        list?.let {
            addDeviceFilters(it)
        }
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


    /**
     * Camera strategy super class
     *
     * @property ctx context
     * @property device see [UsbDevice]
     * @constructor Create camera by inherit it
     */
    abstract class ICamera(val ctx: Context, val device: UsbDevice): Handler.Callback {
        private var mCameraThread: HandlerThread? = null
        private var mRenderManager: RenderManager?  = null
        private var mCameraView: Any? = null
        private var mCameraStateCallback: ICameraStateCallBack? = null
        private var mSizeChangedFuture: SettableFuture<Pair<Int, Int>>? = null
        protected var mContext = ctx
        protected var mCameraRequest: CameraRequest? = null
        protected var mCameraHandler: Handler? = null
        protected var isPreviewed: Boolean = false
        protected var isNeedGLESRender: Boolean = false
        protected var mCtrlBlock: USBMonitor.UsbControlBlock? = null
        protected val mMainHandler: Handler by lazy {
            Handler(Looper.getMainLooper())
        }

        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                MSG_START_PREVIEW -> {
                    val previewWidth = mCameraRequest!!.previewWidth
                    val previewHeight = mCameraRequest!!.previewHeight
                    when (val cameraView = mCameraView) {
                        is IAspectRatio -> {
                            if (mCameraRequest!!.isAspectRatioShow) {
                                cameraView.setAspectRatio(previewWidth, previewHeight)
                            }
                            cameraView
                        }
                        else -> {
                            null
                        }
                    }.also { view->
                        isNeedGLESRender = isGLESRender()
                        if (! isNeedGLESRender && view != null) {
                            openCameraInternal(view)
                            return true
                        }
                        // use opengl render
                        // if surface is null, force off screen render whatever mode
                        // and use init preview size（measure size） for render size
                        val measureSize = try {
                            mSizeChangedFuture = SettableFuture()
                            mSizeChangedFuture?.get(2000, TimeUnit.MILLISECONDS)
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
                            Logger.i(TAG, "surface measure size $measureSize")
                        }
                        val screenWidth = view?.getSurfaceWidth() ?: previewWidth
                        val screenHeight = view?.getSurfaceHeight() ?: previewHeight
                        val surface = view?.getSurface()
                        mRenderManager = RenderManager(ctx, previewWidth, previewHeight)
                        mRenderManager?.startRenderScreen(screenWidth, screenHeight, surface, object : RenderManager.CameraSurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?) {
                                if (surfaceTexture == null) {
                                    closeCamera()
                                    postStateEvent(ICameraStateCallBack.State.ERROR, "create camera surface failed")
                                    return
                                }
                                openCameraInternal(surfaceTexture)
                            }
                        })
                        mRenderManager?.setRotateType(mCameraRequest!!.defaultRotateType)
                    }
                }
                MSG_STOP_PREVIEW -> {
                    cancelSizeWait()
                    closeCameraInternal()
                    mRenderManager?.stopRenderScreen()
                    mRenderManager = null
                }
            }
            return true
        }

        protected abstract fun <T> openCameraInternal(cameraView: T)
        protected abstract fun closeCameraInternal()

        /**
         * should use opengl, recommend
         *
         * @return default depend on device opengl version, >=2.0 is true
         */
        private fun isGLESRender(): Boolean = OpenGLUtils.isGlEsSupported(ctx)

        /**
         * Rotate camera render angle
         *
         * @param type rotate angle, null means rotating nothing
         * see [RotateType.ANGLE_90], [RotateType.ANGLE_270],...etc.
         */
        fun setRotateType(type: RotateType?) {
            mRenderManager?.setRotateType(type)
        }

        /**
         * Set render size
         *
         * @param width surface width
         * @param height surface height
         */
        fun setRenderSize(width: Int, height: Int) {
            mSizeChangedFuture?.set(Pair(width, height))
            mRenderManager?.setRenderSize(width, height)
        }

        /**
         * Post camera state to main thread
         *
         * @param state see [ICameraStateCallBack.State]
         * @param msg detail msg
         */
        protected fun postStateEvent(state: ICameraStateCallBack.State, msg: String? = null) {
            mMainHandler.post {
                mCameraStateCallback?.onCameraState(this, state, msg)
            }
        }

        /**
         * Set usb control block, when the uvc device was granted permission
         *
         * @param ctrlBlock see [USBMonitor.OnDeviceConnectListener]#onConnectedDev
         */
        fun setUsbControlBlock(ctrlBlock: USBMonitor.UsbControlBlock?) {
            this.mCtrlBlock = ctrlBlock
        }

        /**
         * Get usb device information
         *
         * @return see [UsbDevice]
         */
        fun getUsbDevice() = device

        /**
         * Get all preview sizes
         *
         * @param aspectRatio aspect ratio
         * @return [PreviewSize] list of camera
         */
        abstract fun getAllPreviewSizes(aspectRatio: Double? = null): MutableList<PreviewSize>

        /**
         * Open camera
         *
         * @param cameraView render surface view，support Surface or SurfaceTexture
         *                      Only SurfaceTexture and TextureView are supported.
         * @param cameraRequest camera request
         */
        fun <T> openCamera(cameraView: T? = null, cameraRequest: CameraRequest? = null) {
            mCameraView = cameraView ?: mCameraView
            mCameraRequest = cameraRequest ?: getDefaultCameraRequest()
            HandlerThread("camera-${System.currentTimeMillis()}").apply {
                start()
            }.let { thread ->
                this.mCameraThread = thread
                thread
            }.also {
                mCameraHandler = Handler(it.looper, this)
                mCameraHandler?.obtainMessage(MSG_START_PREVIEW)?.sendToTarget()
            }
        }

        /**
         * Close camera
         */
        fun closeCamera() {
            mCameraHandler?.obtainMessage(MSG_STOP_PREVIEW)?.sendToTarget()
            mCameraThread?.quitSafely()
            mCameraThread = null
            mCameraHandler = null
        }

        /**
         * check if camera opened
         *
         * @return camera open status, true or false
         */
        fun isCameraOpened() = isPreviewed

        /**
         * Get current camera request
         *
         * @return see [CameraRequest], can be null
         */
        fun getCameraRequest() = mCameraRequest

        /**
         * Update resolution
         *
         * @param width camera preview width, see [PreviewSize]
         * @param height camera preview height, [PreviewSize]
         * @return result of operation
         */
        fun updateResolution(width: Int, height: Int) {
            if (mCameraRequest == null) {
                Logger.w(TAG, "updateResolution failed, please open camera first.")
                return
            }
            mCameraRequest?.apply {
                if (previewWidth == width && previewHeight == height) {
                    return@apply
                }
                Logger.i(TAG, "updateResolution: width = $width, height = $height")
                closeCamera()
                mMainHandler.postDelayed({
                    previewWidth = width
                    previewHeight = height
                    openCamera(mCameraView, mCameraRequest)
                }, 1000)
            }
        }

        /**
         * Set camera state call back
         *
         * @param callback camera be opened or closed
         */
        fun setCameraStateCallBack(callback: ICameraStateCallBack?) {
            this.mCameraStateCallback = callback
        }

        fun getSuitableSize(maxWidth: Int, maxHeight: Int): PreviewSize {
            val sizeList = getAllPreviewSizes()
            if (sizeList.isNullOrEmpty()) {
                return PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT)
            }
            // find it
            sizeList.find {
                (it.width == maxWidth && it.height == maxHeight)
            }.also { size ->
                size ?: return@also
                return size
            }
            // find the same aspectRatio
            val aspectRatio = maxWidth.toFloat() / maxHeight
            sizeList.find {
                val w = it.width
                val h = it.height
                val ratio = w.toFloat() / h
                ratio == aspectRatio && w <= maxWidth && h <= maxHeight
            }.also { size ->
                size ?: return@also
                return size
            }
            // find the closest aspectRatio
            var minDistance: Int = maxWidth
            var closetSize = sizeList[0]
            sizeList.forEach { size ->
                if (minDistance >= abs((maxWidth - size.width))) {
                    minDistance = abs(maxWidth - size.width)
                    closetSize = size
                }
            }
            // use default
            sizeList.find {
                (it.width == DEFAULT_PREVIEW_WIDTH || it.height == DEFAULT_PREVIEW_HEIGHT)
            }.also { size ->
                size ?: return@also
                return size
            }
            return closetSize
        }

        fun isPreviewSizeSupported(previewSize: PreviewSize): Boolean {
            return getAllPreviewSizes().find {
                it.width == previewSize.width && it.height == previewSize.height
            } != null
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
