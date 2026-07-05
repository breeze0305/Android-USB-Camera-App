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
package com.jiangdg.ausbc.camera.bean

import androidx.annotation.Keep
import com.jiangdg.ausbc.render.env.RotateType


/** Camera request parameters
 *
 * @author Created by jiangdg on 2021/12/20
 */
@Keep
class CameraRequest private constructor() {
    var previewWidth: Int = DEFAULT_WIDTH
    var previewHeight: Int = DEFAULT_HEIGHT
    var isAspectRatioShow: Boolean = true
    var defaultRotateType: RotateType = RotateType.ANGLE_0
    var previewFormat: PreviewFormat = PreviewFormat.FORMAT_MJPEG
    var previewMinFps: Int = DEFAULT_MIN_FPS
    var previewMaxFps: Int = DEFAULT_MAX_FPS

    /**
     * Camera request builder
     *
     * @constructor Create empty Camera request builder
     */
    class Builder {
        private val mRequest by lazy {
            CameraRequest()
        }

        /**
         * Set preview width
         *
         * @param width camera preview width
         * @return see [Builder]
         */
        fun setPreviewWidth(width: Int): Builder {
            mRequest.previewWidth = width
            return this
        }

        /**
         * Set preview height
         *
         * @param height camera preview height
         * @return [Builder]
         */
        fun setPreviewHeight(height: Int): Builder {
            mRequest.previewHeight = height
            return this
        }

        /**
         * Set aspect ratio show
         *
         * @param isAspectRatioShow  default is true
         * @return see [Builder]
         */
        fun setAspectRatioShow(isAspectRatioShow: Boolean): Builder {
            mRequest.isAspectRatioShow = isAspectRatioShow
            return this
        }

        /**
         * Set default rotate type, only OPENGL mode useful
         *
         * @param defaultRotateType default is [RotateType.ANGLE_0]
         * @return  see [Builder]
         */
        fun setDefaultRotateType(defaultRotateType: RotateType): Builder {
            mRequest.defaultRotateType = defaultRotateType
            return this
        }

        /**
         * Set preview format
         *
         * @param format preview format, default is [PreviewFormat.FORMAT_MJPEG]
         * @return see [Builder]
         */
        fun setPreviewFormat(format: PreviewFormat): Builder {
            mRequest.previewFormat = format
            return this
        }

        /**
         * Set the requested preview FPS range. UVC will choose a supported
         * frame interval inside this range.
         */
        fun setPreviewFpsRange(minFps: Int, maxFps: Int): Builder {
            mRequest.previewMinFps = minFps.coerceAtLeast(1)
            mRequest.previewMaxFps = maxFps.coerceAtLeast(mRequest.previewMinFps)
            return this
        }

        /**
         * Create a CameraRequest
         *
         * @return see [CameraRequest]
         */
        fun create(): CameraRequest {
            return mRequest
        }
    }

    /**
     * Preview format
     *
     * FORMAT_MJPEG: default format with high frame rate
     * FORMAT_YUYV: yuv format with lower frame rate
     */
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
