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
package com.jiangdg.ausbc.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.view.TextureView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.utils.CameraUtils
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.Utils
import com.jiangdg.uvc.UVCCamera

/** UVC Camera
 *
 * @author Created by jiangdg on 2023/1/15
 */
class CameraUVC(ctx: Context, device: UsbDevice) : MultiCameraClient.ICamera(ctx, device) {
    private var mUvcCamera: UVCCamera? = null
    private val mCameraPreviewSize by lazy {
        arrayListOf<PreviewSize>()
    }

    override fun getAllPreviewSizes(aspectRatio: Double?): MutableList<PreviewSize> {
        val previewSizeList = arrayListOf<PreviewSize>()
        val isMjpegFormat = mCameraRequest?.previewFormat == CameraRequest.PreviewFormat.FORMAT_MJPEG
        if (isMjpegFormat && (mUvcCamera?.supportedSizeList?.isNotEmpty() == true)) {
            mUvcCamera?.supportedSizeList
        }  else {
            mUvcCamera?.getSupportedSizeList(UVCCamera.FRAME_FORMAT_YUYV)
        }?.let { sizeList ->
            if (sizeList.size > mCameraPreviewSize.size) {
                mCameraPreviewSize.clear()
                sizeList.forEach { size->
                    val width = size.width
                    val height = size.height
                    mCameraPreviewSize.add(PreviewSize(width, height))
                }
            }
            if (Utils.debugCamera) {
                Logger.i(TAG, "aspect ratio = $aspectRatio, supportedSizeList = $sizeList")
            }
            mCameraPreviewSize
        }?.onEach { size ->
            val width = size.width
            val height = size.height
            val ratio = width.toDouble() / height
            if (aspectRatio == null || aspectRatio == ratio) {
                previewSizeList.add(PreviewSize(width, height))
            }
        }
        return previewSizeList
    }

    override fun <T> openCameraInternal(cameraView: T) {
        if (Utils.isTargetSdkOverP(ctx) && !CameraUtils.hasCameraPermission(ctx)) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR, "Has no CAMERA permission.")
            Logger.e(TAG,"open camera failed, need Manifest.permission.CAMERA permission when targetSdk>=28")
            return
        }
        if (mCtrlBlock == null) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR, "Usb control block can not be null ")
            return
        }
        // 1. create a UVCCamera
        val request = mCameraRequest!!
        try {
            mUvcCamera = UVCCamera().apply {
                open(mCtrlBlock)
            }
        } catch (e: RuntimeException) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR, "open camera failed ${e.localizedMessage}")
            Logger.e(TAG, "open camera failed.", e)
            return
        }

        // 2. set preview size and register preview callback
        var previewSize = getSuitableSize(request.previewWidth, request.previewHeight).apply {
            mCameraRequest!!.previewWidth = width
            mCameraRequest!!.previewHeight = height
        }
        val previewFormat = if (mCameraRequest?.previewFormat == CameraRequest.PreviewFormat.FORMAT_YUYV) {
            UVCCamera.FRAME_FORMAT_YUYV
        } else {
            UVCCamera.FRAME_FORMAT_MJPEG
        }
        try {
            Logger.i(TAG, "getSuitableSize: $previewSize")
            if (! isPreviewSizeSupported(previewSize)) {
                closeCamera()
                postStateEvent(ICameraStateCallBack.State.ERROR, "unsupported preview size")
                Logger.e(TAG, "open camera failed, preview size($previewSize) unsupported-> ${mUvcCamera?.supportedSizeList}")
                return
            }
            // if give custom minFps or maxFps or unsupported preview size
            // this method will fail
            mUvcCamera?.setPreviewSize(
                previewSize.width,
                previewSize.height,
                request.previewMinFps,
                request.previewMaxFps,
                previewFormat,
                UVCCamera.DEFAULT_BANDWIDTH
            )
        } catch (e: RuntimeException) {
            Logger.w(TAG, "setPreviewSize failed(format is $previewFormat), try fallback format", e)
            try {
                previewSize = getSuitableSize(request.previewWidth, request.previewHeight).apply {
                    mCameraRequest!!.previewWidth = width
                    mCameraRequest!!.previewHeight = height
                }
                if (! isPreviewSizeSupported(previewSize)) {
                    postStateEvent(ICameraStateCallBack.State.ERROR, "unsupported preview size")
                    closeCamera()
                    Logger.e(TAG, "open camera failed, preview size($previewSize) unsupported-> ${mUvcCamera?.supportedSizeList}")
                    return
                }
                mUvcCamera?.setPreviewSize(
                    previewSize.width,
                    previewSize.height,
                    request.previewMinFps,
                    request.previewMaxFps,
                    if (previewFormat == UVCCamera.FRAME_FORMAT_YUYV) {
                        UVCCamera.FRAME_FORMAT_MJPEG
                    } else {
                        UVCCamera.FRAME_FORMAT_YUYV
                    },
                    UVCCamera.DEFAULT_BANDWIDTH
                )
            } catch (fallbackError: RuntimeException) {
                closeCamera()
                postStateEvent(ICameraStateCallBack.State.ERROR, "err: ${fallbackError.localizedMessage}")
                Logger.e(TAG, "setPreviewSize failed using fallback format", fallbackError)
                return
            }
        }
        // 3. start preview
        try {
            when(cameraView) {
                is SurfaceTexture -> {
                    mUvcCamera?.setPreviewTexture(cameraView)
                }
                is TextureView -> {
                    mUvcCamera?.setPreviewTexture(cameraView.surfaceTexture)
                }
                else -> {
                    closeCamera()
                    postStateEvent(ICameraStateCallBack.State.ERROR, "Unsupported preview surface")
                    Logger.e(TAG, "open camera failed, unsupported preview surface: $cameraView")
                    return
                }
            }
            mUvcCamera?.startPreview()
        } catch (e: RuntimeException) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR, "start preview failed ${e.localizedMessage}")
            Logger.e(TAG, "start preview failed.", e)
            return
        }
        isPreviewed = true
        postStateEvent(ICameraStateCallBack.State.OPENED)
        if (Utils.debugCamera) {
            Logger.i(TAG, " start preview, name = ${device.deviceName}, preview=$previewSize")
        }
    }

    override fun closeCameraInternal() {
        postStateEvent(ICameraStateCallBack.State.CLOSED)
        isPreviewed = false
        mUvcCamera?.destroy()
        mUvcCamera = null
        if (Utils.debugCamera) {
            Logger.i(TAG, " stop preview, name = ${device.deviceName}")
        }
    }

    companion object {
        private const val TAG = "CameraUVC"
        private const val MIN_FS = 1
        private const val MAX_FPS = 61
    }
}
