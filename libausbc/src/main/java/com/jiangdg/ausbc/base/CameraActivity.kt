package com.jiangdg.ausbc.base

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.SettableFuture
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.usb.USBMonitor
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

abstract class CameraActivity: AppCompatActivity(), ICameraStateCallBack {
    private var mCameraView: IAspectRatio? = null
    private var mCameraClient: MultiCameraClient? = null
    private val mCameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()
    private var mCurrentCamera: SettableFuture<MultiCameraClient.ICamera>? = null

    private val mRequestPermission: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getRootView(layoutInflater))
        initView()
        initData()
    }

    override fun onDestroy() {
        super.onDestroy()
        clear()
    }

    open fun initView() {
        val cameraView = getCameraView()
        val textureView = cameraView as? TextureView
        mCameraView = if (textureView != null) cameraView else null
        if (textureView == null) {
            registerMultiCamera()
            return
        }

        handleTextureView(textureView)
        getCameraViewContainer()?.apply {
            removeAllViews()
            addView(textureView, getViewLayoutParams(this))
        }
    }

    open fun initData() {}

    open fun clear() {
        unRegisterMultiCamera()
    }

    protected fun registerMultiCamera() {
        mCameraClient = MultiCameraClient(this, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                if (mCameraMap.containsKey(device.deviceId)) {
                    return
                }
                generateCamera(this@CameraActivity, device).apply {
                    mCameraMap[device.deviceId] = this
                }
                // Initiate permission request when device insertion is detected
                // If you want to open the specified camera, you need to override getDefaultCamera()
                if (mRequestPermission.get()) {
                    return
                }
                getDefaultCamera()?.apply {
                    if (vendorId == device.vendorId && productId == device.productId) {
                        Logger.i(TAG, "default camera pid: $productId, vid: $vendorId")
                        requestPermission(device)
                    }
                    return
                }
                requestPermission(device)
            }

            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                }
                mRequestPermission.set(false)
                cancelCurrentCamera("camera detached")
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                mCameraMap[device.deviceId]?.apply {
                    setUsbControlBlock(ctrlBlock)
                }?.also { camera ->
                    cancelCurrentCamera("camera connected")
                    mCurrentCamera = SettableFuture()
                    mCurrentCamera?.set(camera)
                    openCamera(mCameraView)
                    Logger.i(TAG, "camera connection. pid: ${device.productId}, vid: ${device.vendorId}")
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                closeCamera()
                mRequestPermission.set(false)
            }

            override fun onCancelDev(device: UsbDevice?) {
                mRequestPermission.set(false)
                cancelCurrentCamera("permission cancelled")
            }
        })
        mCameraClient?.register()
    }

    protected fun unRegisterMultiCamera() {
        mCameraMap.values.forEach {
            it.closeCamera()
        }
        mCameraMap.clear()
        mCameraClient?.unRegister()
        mCameraClient?.destroy()
        mCameraClient = null
    }

    protected fun getDeviceList() = mCameraClient?.getDeviceList()

    private fun handleTextureView(textureView: TextureView) {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                registerMultiCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surfaceSizeChanged(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                unRegisterMultiCamera()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
        }
    }

    protected fun getCurrentCamera(): MultiCameraClient.ICamera? {
        return try {
            mCurrentCamera?.get(2, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Logger.w(TAG, "get current camera timed out", e)
            null
        } catch (e: CancellationException) {
            Logger.w(TAG, "get current camera cancelled", e)
            null
        } catch (e: ExecutionException) {
            Logger.e(TAG, "get current camera failed", e)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Logger.w(TAG, "get current camera interrupted", e)
            null
        }
    }

    protected fun requestPermission(device: UsbDevice?) {
        if (device == null) {
            Logger.w(TAG, "ignore permission request for null USB device")
            return
        }
        mRequestPermission.set(true)
        mCameraClient?.requestPermission(device)
    }

    protected open fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    protected open fun getDefaultCamera(): UsbDevice? = null

    protected fun switchCamera(usbDevice: UsbDevice) {
        getCurrentCamera()?.closeCamera()
        try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Logger.w(TAG, "switch camera wait interrupted", e)
        }
        requestPermission(usbDevice)
    }

    private fun cancelCurrentCamera(reason: String) {
        try {
            mCurrentCamera?.cancel(true)
        } catch (e: RuntimeException) {
            Logger.w(TAG, "cancel current camera failed: $reason", e)
        } finally {
            mCurrentCamera = null
        }
    }

    protected fun isCameraOpened() = getCurrentCamera()?.isCameraOpened()  ?: false

    protected fun updateResolution(width: Int, height: Int) {
        getCurrentCamera()?.updateResolution(width, height)
    }

    protected fun getAllPreviewSizes(aspectRatio: Double? = null) = getCurrentCamera()?.getAllPreviewSizes(aspectRatio)

    protected fun getCurrentPreviewSize(): PreviewSize? {
        return getCurrentCamera()?.getCameraRequest()?.let {
            PreviewSize(it.previewWidth, it.previewHeight)
        }
    }

    protected fun setRotateType(type: RotateType) {
        getCurrentCamera()?.setRotateType(type)
    }

    protected fun openCamera(st: IAspectRatio? = null) {
        val camera = getCurrentCamera() ?: return
        camera.openCamera(st as? TextureView, getCameraRequest())
        camera.setCameraStateCallBack(this)
    }

    protected fun closeCamera() {
        getCurrentCamera()?.closeCamera()
    }

    private fun surfaceSizeChanged(surfaceWidth: Int, surfaceHeight: Int) {
        getCurrentCamera()?.setRenderSize(surfaceWidth, surfaceHeight)
    }

    private fun getViewLayoutParams(viewGroup: ViewGroup): ViewGroup.LayoutParams {
        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        return if (viewGroup is FrameLayout) {
            FrameLayout.LayoutParams(width, height, getGravity())
        } else {
            ViewGroup.LayoutParams(width, height)
        }
    }

    protected abstract fun getRootView(layoutInflater: LayoutInflater): View?

    protected abstract fun getCameraView(): IAspectRatio?

    protected abstract fun getCameraViewContainer(): ViewGroup?

    protected open fun getGravity() = Gravity.CENTER

    protected open fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .setDefaultRotateType(RotateType.ANGLE_0)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setAspectRatioShow(true)
            .create()
    }

    companion object {
        private const val TAG = "CameraActivity"
    }
}
