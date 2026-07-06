package com.jiangdg.ausbc.camera.bean

import androidx.annotation.Keep
import com.jiangdg.ausbc.render.env.RotateType

@Keep
class CameraRequest private constructor() {
    var previewWidth: Int = DEFAULT_WIDTH
    var previewHeight: Int = DEFAULT_HEIGHT
    var isAspectRatioShow: Boolean = true
    var defaultRotateType: RotateType = RotateType.ANGLE_0
    var previewFormat: PreviewFormat = PreviewFormat.FORMAT_MJPEG
    var previewMinFps: Int = DEFAULT_MIN_FPS
    var previewMaxFps: Int = DEFAULT_MAX_FPS
    var playCameraAudio: Boolean = false

    class Builder {
        private val request = CameraRequest()

        fun setPreviewWidth(width: Int): Builder = apply {
            request.previewWidth = width
        }

        fun setPreviewHeight(height: Int): Builder = apply {
            request.previewHeight = height
        }

        fun setAspectRatioShow(show: Boolean): Builder = apply {
            request.isAspectRatioShow = show
        }

        fun setDefaultRotateType(type: RotateType): Builder = apply {
            request.defaultRotateType = type
        }

        fun setPreviewFormat(format: PreviewFormat): Builder = apply {
            request.previewFormat = format
        }

        fun setPreviewFpsRange(minFps: Int, maxFps: Int): Builder = apply {
            request.previewMinFps = minFps.coerceAtLeast(1)
            request.previewMaxFps = maxFps.coerceAtLeast(request.previewMinFps)
        }

        fun setPlayCameraAudio(enabled: Boolean): Builder = apply {
            request.playCameraAudio = enabled
        }

        fun create(): CameraRequest = request
    }

    enum class PreviewFormat {
        FORMAT_MJPEG,
        FORMAT_YUYV
    }

    companion object {
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
        private const val DEFAULT_MIN_FPS = 1
        private const val DEFAULT_MAX_FPS = 61
    }
}
