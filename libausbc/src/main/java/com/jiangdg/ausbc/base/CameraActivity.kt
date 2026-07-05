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
package com.jiangdg.ausbc.base

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.callback.*
import com.jiangdg.ausbc.camera.CameraUVC
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


/** Base activity for one UVC camera.
 *
 * @author Created by jiangdg on 2023/2/3
 */
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
        when (val cameraView = getCameraView()) {
            is TextureView -> {
                handleTextureView(cameraView)
                cameraView
            }
            else -> {
                null
            }
        }.apply {
            mCameraView = this
            // offscreen render
            if (this == null) {
                registerMultiCamera()
                return
            }
        }?.also { view->
            getCameraViewContainer()?.apply {
                removeAllViews()
                addView(view, getViewLayoutParams(this))
            }
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

    /**
     * Get current opened camera
     *
     * @return current camera, see [MultiCameraClient.ICamera]
     */
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

    /**
     * Request permission
     *
     * @param device see [UsbDevice]
     */
    protected fun requestPermission(device: UsbDevice?) {
        mRequestPermission.set(true)
        mCameraClient?.requestPermission(device)
    }

    /**
     * Generate camera
     *
     * @param ctx context [Context]
     * @param device Usb device, see [UsbDevice]
     * @return Inheritor assignment camera api policy
     */
    protected open fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    /**
     * Get default camera
     *
     * @return Open camera by default, should be [UsbDevice]
     */
    protected open fun getDefaultCamera(): UsbDevice? = null

    /**
     * Switch camera
     *
     * @param usbDevice camera usb device
     */
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

    /**
     * Is camera opened
     *
     * @return camera open status
     */
    protected fun isCameraOpened() = getCurrentCamera()?.isCameraOpened()  ?: false

    /**
     * Update resolution
     *
     * @param width camera preview width
     * @param height camera preview height
     */
    protected fun updateResolution(width: Int, height: Int) {
        getCurrentCamera()?.updateResolution(width, height)
    }

    /**
     * Get all preview sizes
     *
     * @param aspectRatio preview size aspect ratio,
     *                      null means getting all preview sizes
     */
    protected fun getAllPreviewSizes(aspectRatio: Double? = null) = getCurrentCamera()?.getAllPreviewSizes(aspectRatio)

    /**
     * Get current preview size
     *
     * @return camera preview size, see [PreviewSize]
     */
    protected fun getCurrentPreviewSize(): PreviewSize? {
        return getCurrentCamera()?.getCameraRequest()?.let {
            PreviewSize(it.previewWidth, it.previewHeight)
        }
    }

    /**
     * Rotate camera angle
     *
     * @param type rotate angle, null means rotating nothing
     * see [RotateType.ANGLE_90], [RotateType.ANGLE_270],...etc.
     */
    protected fun setRotateType(type: RotateType) {
        getCurrentCamera()?.setRotateType(type)
    }

    protected fun openCamera(st: IAspectRatio? = null) {
        when (st) {
            is TextureView -> {
                st
            }
            else -> {
                null
            }
        }.apply {
            getCurrentCamera()?.openCamera(this, getCameraRequest())
            getCurrentCamera()?.setCameraStateCallBack(this@CameraActivity)
        }
    }

    protected fun closeCamera() {
        getCurrentCamera()?.closeCamera()
    }

    private fun surfaceSizeChanged(surfaceWidth: Int, surfaceHeight: Int) {
        getCurrentCamera()?.setRenderSize(surfaceWidth, surfaceHeight)
    }

    private fun getViewLayoutParams(viewGroup: ViewGroup): ViewGroup.LayoutParams {
        return when(viewGroup) {
            is FrameLayout -> {
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    getGravity()
                )
            }
            is LinearLayout -> {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = getGravity()
                }
            }
            is RelativeLayout -> {
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                ).apply{
                    when(getGravity()) {
                        Gravity.TOP -> {
                            addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                        }
                        Gravity.BOTTOM -> {
                            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                        }
                        else -> {
                            addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE)
                            addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE)
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported container view, " +
                    "you can use FrameLayout or LinearLayout or RelativeLayout")
        }
    }

    /**
     * Build the activity content view.
     */
    protected abstract fun getRootView(layoutInflater: LayoutInflater): View?

    /**
     * Get camera view
     *
     * @return CameraView, such as AspectRatioTextureView etc.
     */
    protected abstract fun getCameraView(): IAspectRatio?

    /**
     * Get camera view container
     *
     * @return camera view container, such as FrameLayout ect
     */
    protected abstract fun getCameraViewContainer(): ViewGroup?

    /**
     * Camera render view show gravity
     */
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
