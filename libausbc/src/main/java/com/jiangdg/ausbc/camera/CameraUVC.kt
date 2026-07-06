package com.jiangdg.ausbc.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.view.TextureView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.audio.CameraAudioPlayer
import com.jiangdg.ausbc.audio.UsbCameraAudioSource
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.utils.CameraUtils
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.Utils
import com.jiangdg.uvc.UVCCamera
import com.jiangdg.utils.Size

class CameraUVC(ctx: Context, device: UsbDevice) : MultiCameraClient.ICamera(ctx, device) {
    private var mUvcCamera: UVCCamera? = null
    private var cameraAudioPlayer: CameraAudioPlayer? = null
    private val cachedPreviewSizes = arrayListOf<PreviewSize>()

    override fun getAllPreviewSizes(aspectRatio: Double?): MutableList<PreviewSize> {
        val sizes = readSupportedSizes().map { PreviewSize(it.width, it.height) }
        cachedPreviewSizes.clear()
        cachedPreviewSizes.addAll(sizes)

        if (Utils.debugCamera) {
            Logger.i(TAG, "aspect ratio = $aspectRatio, supportedSizeList = $sizes")
        }

        return cachedPreviewSizes
            .filterTo(arrayListOf()) { size ->
                aspectRatio == null || size.width.toDouble() / size.height == aspectRatio
            }
    }

    override fun <T> openCameraInternal(cameraView: T) {
        val request = mCameraRequest
        if (request == null) {
            failOpen("camera request can not be null")
            return
        }
        if (Utils.isTargetSdkOverP(ctx) && !CameraUtils.hasCameraPermission(ctx)) {
            failOpen("Has no CAMERA permission.")
            Logger.e(TAG, "open camera failed, need Manifest.permission.CAMERA permission when targetSdk>=28")
            return
        }
        val ctrlBlock = mCtrlBlock
        if (ctrlBlock == null) {
            failOpen("Usb control block can not be null")
            return
        }

        val camera = try {
            UVCCamera().apply { open(ctrlBlock) }
        } catch (e: RuntimeException) {
            failOpen("open camera failed ${e.localizedMessage}")
            Logger.e(TAG, "open camera failed.", e)
            return
        }
        mUvcCamera = camera

        val previewSize = configurePreviewSize(camera, request) ?: return
        val previewTexture = resolvePreviewTexture(cameraView) ?: run {
            failOpen("Unsupported preview surface")
            Logger.e(TAG, "open camera failed, unsupported preview surface: $cameraView")
            return
        }

        try {
            camera.setPreviewTexture(previewTexture)
            camera.startPreview()
        } catch (e: RuntimeException) {
            failOpen("start preview failed ${e.localizedMessage}")
            Logger.e(TAG, "start preview failed.", e)
            return
        }

        isPreviewed = true
        startCameraAudioIfNeeded(request)
        postStateEvent(ICameraStateCallBack.State.OPENED)
        if (Utils.debugCamera) {
            Logger.i(TAG, "start preview, name = ${device.deviceName}, preview=$previewSize")
        }
    }

    override fun closeCameraInternal() {
        postStateEvent(ICameraStateCallBack.State.CLOSED)
        isPreviewed = false
        stopCameraAudio()
        mUvcCamera?.destroy()
        mUvcCamera = null
        if (Utils.debugCamera) {
            Logger.i(TAG, "stop preview, name = ${device.deviceName}")
        }
    }

    private fun readSupportedSizes(): List<Size> {
        val camera = mUvcCamera ?: return emptyList()
        val isMjpeg = mCameraRequest?.previewFormat == CameraRequest.PreviewFormat.FORMAT_MJPEG
        return if (isMjpeg && camera.supportedSizeList.isNotEmpty()) {
            camera.supportedSizeList
        } else {
            camera.getSupportedSizeList(UVCCamera.FRAME_FORMAT_YUYV)
        }
    }

    private fun configurePreviewSize(camera: UVCCamera, request: CameraRequest): PreviewSize? {
        var previewSize = getSuitableSize(request.previewWidth, request.previewHeight).also {
            request.previewWidth = it.width
            request.previewHeight = it.height
        }
        val requestedFormat = toUvcFrameFormat(request.previewFormat)

        if (setPreviewSize(camera, request, previewSize, requestedFormat)) {
            return previewSize
        }

        val fallbackFormat = alternateFormat(requestedFormat)
        Logger.w(TAG, "setPreviewSize failed(format is $requestedFormat), try fallback format")
        previewSize = getSuitableSize(request.previewWidth, request.previewHeight).also {
            request.previewWidth = it.width
            request.previewHeight = it.height
        }
        return if (setPreviewSize(camera, request, previewSize, fallbackFormat)) {
            previewSize
        } else {
            failOpen("set preview size failed")
            null
        }
    }

    private fun setPreviewSize(
        camera: UVCCamera,
        request: CameraRequest,
        previewSize: PreviewSize,
        previewFormat: Int
    ): Boolean {
        if (!isPreviewSizeSupported(previewSize)) {
            Logger.e(TAG, "open camera failed, preview size($previewSize) unsupported-> ${camera.supportedSizeList}")
            return false
        }
        return try {
            Logger.i(TAG, "getSuitableSize: $previewSize")
            camera.setPreviewSize(
                previewSize.width,
                previewSize.height,
                request.previewMinFps,
                request.previewMaxFps,
                previewFormat,
                UVCCamera.DEFAULT_BANDWIDTH
            )
            true
        } catch (e: RuntimeException) {
            Logger.w(TAG, "setPreviewSize failed(format is $previewFormat)", e)
            false
        }
    }

    private fun resolvePreviewTexture(cameraView: Any?): SurfaceTexture? {
        return when (cameraView) {
            is SurfaceTexture -> cameraView
            is TextureView -> cameraView.surfaceTexture
            else -> null
        }
    }

    private fun startCameraAudioIfNeeded(request: CameraRequest) {
        if (!request.playCameraAudio) return
        val ctrlBlock = mCtrlBlock ?: run {
            Logger.w(TAG, "camera audio skipped: missing USB control block")
            return
        }
        if (!CameraUtils.isCameraContainsMic(device)) {
            Logger.w(TAG, "camera audio skipped: USB device has no audio interface")
            return
        }
        val source = UsbCameraAudioSource(ctrlBlock)
        if (!source.isAvailable) {
            Logger.w(TAG, "camera audio skipped: UAC native library is unavailable")
            return
        }
        cameraAudioPlayer = CameraAudioPlayer(source) { message ->
            Logger.w(TAG, "camera audio playback failed: $message")
        }.also { it.start() }
    }

    private fun stopCameraAudio() {
        cameraAudioPlayer?.stop()
        cameraAudioPlayer = null
    }

    private fun failOpen(message: String) {
        closeCamera()
        postStateEvent(ICameraStateCallBack.State.ERROR, message)
    }

    private fun toUvcFrameFormat(format: CameraRequest.PreviewFormat): Int {
        return if (format == CameraRequest.PreviewFormat.FORMAT_YUYV) {
            UVCCamera.FRAME_FORMAT_YUYV
        } else {
            UVCCamera.FRAME_FORMAT_MJPEG
        }
    }

    private fun alternateFormat(format: Int): Int {
        return if (format == UVCCamera.FRAME_FORMAT_YUYV) {
            UVCCamera.FRAME_FORMAT_MJPEG
        } else {
            UVCCamera.FRAME_FORMAT_YUYV
        }
    }

    companion object {
        private const val TAG = "CameraUVC"
    }
}
