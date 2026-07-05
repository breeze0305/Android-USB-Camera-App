/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCPreview.cpp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

#include <stdlib.h>
#include <unistd.h>

#if 1	// set 1 if you don't need debug log
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// w/o LOGV/LOGD/MARK
	#endif
	#undef USE_LOGALL
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
//	#undef NDEBUG
#endif

#include "utilbase.h"
#include "UVCPreview.h"
#include "libuvc_internal.h"

#define	LOCAL_DEBUG 0
#define MAX_FRAME 4
#define PREVIEW_PIXEL_BYTES 4	// RGBA/RGBX
#define FRAME_POOL_SZ MAX_FRAME + 2


UVCPreview::UVCPreview(uvc_device_handle_t *devh)
:	mPreviewWindow(NULL),
	mDeviceHandle(devh),
	requestWidth(DEFAULT_PREVIEW_WIDTH),
	requestHeight(DEFAULT_PREVIEW_HEIGHT),
	requestMinFps(DEFAULT_PREVIEW_FPS_MIN),
	requestMaxFps(DEFAULT_PREVIEW_FPS_MAX),
	requestMode(DEFAULT_PREVIEW_MODE),
	requestBandwidth(DEFAULT_BANDWIDTH),
	frameWidth(DEFAULT_PREVIEW_WIDTH),
	frameHeight(DEFAULT_PREVIEW_HEIGHT),
	frameBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * 2),	// YUYV
	frameMode(0),
	previewBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * PREVIEW_PIXEL_BYTES),
	previewFormat(WINDOW_FORMAT_RGBA_8888),
	mIsRunning(false) {

	ENTER();
	pthread_cond_init(&preview_sync, NULL);
	pthread_mutex_init(&preview_mutex, NULL);

	pthread_mutex_init(&pool_mutex, NULL);
	EXIT();
}

UVCPreview::~UVCPreview() {

	ENTER();
	if (mPreviewWindow)
		ANativeWindow_release(mPreviewWindow);
	mPreviewWindow = NULL;
	clearPreviewFrame();
	clear_pool();
	pthread_mutex_destroy(&preview_mutex);
	pthread_cond_destroy(&preview_sync);
	pthread_mutex_destroy(&pool_mutex);
	EXIT();
}

/**
 * get uvc_frame_t from frame pool
 * if pool is empty, create new frame
 * this function does not confirm the frame size
 * and you may need to confirm the size
 */
uvc_frame_t *UVCPreview::get_frame(size_t data_bytes) {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&pool_mutex);
	{
		if (!mFramePool.isEmpty()) {
			frame = mFramePool.last();
		}
	}
	pthread_mutex_unlock(&pool_mutex);
	if UNLIKELY(!frame) {
		LOGW("allocate new frame");
		frame = uvc_allocate_frame(data_bytes);
	}
	return frame;
}

void UVCPreview::recycle_frame(uvc_frame_t *frame) {
	pthread_mutex_lock(&pool_mutex);
	if (LIKELY(mFramePool.size() < FRAME_POOL_SZ)) {
		mFramePool.put(frame);
		frame = NULL;
	}
	pthread_mutex_unlock(&pool_mutex);
	if (UNLIKELY(frame)) {
		uvc_free_frame(frame);
	}
}


void UVCPreview::init_pool(size_t data_bytes) {
	ENTER();

	clear_pool();
	pthread_mutex_lock(&pool_mutex);
	{
		for (int i = 0; i < FRAME_POOL_SZ; i++) {
			mFramePool.put(uvc_allocate_frame(data_bytes));
		}
	}
	pthread_mutex_unlock(&pool_mutex);

	EXIT();
}

void UVCPreview::clear_pool() {
	ENTER();

	pthread_mutex_lock(&pool_mutex);
	{
		const int n = mFramePool.size();
		for (int i = 0; i < n; i++) {
			uvc_free_frame(mFramePool[i]);
		}
		mFramePool.clear();
	}
	pthread_mutex_unlock(&pool_mutex);
	EXIT();
}

inline const bool UVCPreview::isRunning() const {return mIsRunning; }

int UVCPreview::setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth) {
	ENTER();
	
	int result = 0;
	if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {
		requestWidth = width;
		requestHeight = height;
		requestMinFps = min_fps;
		requestMaxFps = max_fps;
		requestMode = mode;
		requestBandwidth = bandwidth;

		uvc_stream_ctrl_t ctrl;
		result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, &ctrl,
			!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
			requestWidth, requestHeight, requestMinFps, requestMaxFps);
	}
	
	RETURN(result, int);
}

int UVCPreview::setPreviewDisplay(ANativeWindow *preview_window) {
	ENTER();
	pthread_mutex_lock(&preview_mutex);
	{
		if (mPreviewWindow != preview_window) {
			if (mPreviewWindow)
				ANativeWindow_release(mPreviewWindow);
			mPreviewWindow = preview_window;
			if (LIKELY(mPreviewWindow)) {
				ANativeWindow_setBuffersGeometry(mPreviewWindow,
					frameWidth, frameHeight, previewFormat);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	RETURN(0, int);
}


void UVCPreview::clearDisplay() {
	ENTER();

	ANativeWindow_Buffer buffer;
	pthread_mutex_lock(&preview_mutex);
	{
		if (LIKELY(mPreviewWindow)) {
			if (LIKELY(ANativeWindow_lock(mPreviewWindow, &buffer, NULL) == 0)) {
				uint8_t *dest = (uint8_t *)buffer.bits;
				const size_t bytes = buffer.width * PREVIEW_PIXEL_BYTES;
				const int stride = buffer.stride * PREVIEW_PIXEL_BYTES;
				for (int i = 0; i < buffer.height; i++) {
					memset(dest, 0, bytes);
					dest += stride;
				}
				ANativeWindow_unlockAndPost(mPreviewWindow);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);

	EXIT();
}

int UVCPreview::startPreview() {
	ENTER();

	int result = EXIT_FAILURE;
	if (!isRunning()) {
		mIsRunning = true;
		pthread_mutex_lock(&preview_mutex);
		{
			if (LIKELY(mPreviewWindow)) {
				result = pthread_create(&preview_thread, NULL, preview_thread_func, (void *)this);
			}
		}
		pthread_mutex_unlock(&preview_mutex);
		if (UNLIKELY(result != EXIT_SUCCESS)) {
			LOGW("UVCCamera::window does not exist/already running/could not create thread etc.");
			mIsRunning = false;
			pthread_mutex_lock(&preview_mutex);
			{
				pthread_cond_signal(&preview_sync);
			}
			pthread_mutex_unlock(&preview_mutex);
		}
	}
	RETURN(result, int);
}

int UVCPreview::stopPreview() {
	ENTER();
	bool b = isRunning();
	if (LIKELY(b)) {
		mIsRunning = false;
        pthread_cond_signal(&preview_sync);
		if (preview_thread && pthread_join(preview_thread, NULL) != EXIT_SUCCESS) {
			LOGW("UVCPreview::terminate preview thread: pthread_join failed");
		}
		clearDisplay();
	}
	clearPreviewFrame();
	// check preview mutex available
	if (pthread_mutex_lock(&preview_mutex) == 0) {
		if (mPreviewWindow) {
			ANativeWindow_release(mPreviewWindow);
			mPreviewWindow = NULL;
		}
		pthread_mutex_unlock(&preview_mutex);
	}
	RETURN(0, int);
}

//**********************************************************************
//
//**********************************************************************
void UVCPreview::uvc_preview_frame_callback(uvc_frame_t *frame, void *vptr_args) {
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if UNLIKELY(!preview->isRunning() || !frame || !frame->frame_format || !frame->data || !frame->data_bytes) return;
	if (UNLIKELY(
		((frame->frame_format != UVC_FRAME_FORMAT_MJPEG) && (frame->actual_bytes < preview->frameBytes))
		|| (frame->width != preview->frameWidth) || (frame->height != preview->frameHeight) )) {

#if LOCAL_DEBUG
		LOGD("broken frame!:format=%d,actual_bytes=%d/%d(%d,%d/%d,%d)",
			frame->frame_format, frame->actual_bytes, preview->frameBytes,
			frame->width, frame->height, preview->frameWidth, preview->frameHeight);
#endif
		return;
	}
	if (LIKELY(preview->isRunning())) {
		uvc_frame_t *copy = preview->get_frame(frame->data_bytes);
		if (UNLIKELY(!copy)) {
#if LOCAL_DEBUG
			LOGE("uvc_callback:unable to allocate duplicate frame!");
#endif
			return;
		}
		uvc_error_t ret = uvc_duplicate_frame(frame, copy);
		if (UNLIKELY(ret)) {
			preview->recycle_frame(copy);
			return;
		}
		preview->addPreviewFrame(copy);
	}
}

void UVCPreview::addPreviewFrame(uvc_frame_t *frame) {

	pthread_mutex_lock(&preview_mutex);
	if (isRunning() && (previewFrames.size() < MAX_FRAME)) {
		previewFrames.put(frame);
		frame = NULL;
		pthread_cond_signal(&preview_sync);
	}
	pthread_mutex_unlock(&preview_mutex);
	if (frame) {
		recycle_frame(frame);
	}
}

uvc_frame_t *UVCPreview::waitPreviewFrame() {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&preview_mutex);
	{
		if (!previewFrames.size()) {
			pthread_cond_wait(&preview_sync, &preview_mutex);
		}
		if (LIKELY(isRunning() && previewFrames.size() > 0)) {
			frame = previewFrames.remove(0);
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	return frame;
}

void UVCPreview::clearPreviewFrame() {
	pthread_mutex_lock(&preview_mutex);
	{
		for (int i = 0; i < previewFrames.size(); i++)
			recycle_frame(previewFrames[i]);
		previewFrames.clear();
	}
	pthread_mutex_unlock(&preview_mutex);
}

void *UVCPreview::preview_thread_func(void *vptr_args) {
	int result;

	ENTER();
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if (LIKELY(preview)) {
		uvc_stream_ctrl_t ctrl;
		result = preview->prepare_preview(&ctrl);
		if (LIKELY(!result)) {
			preview->do_preview(&ctrl);
		}
	}
	PRE_EXIT();
	pthread_exit(NULL);
}

int UVCPreview::prepare_preview(uvc_stream_ctrl_t *ctrl) {
	uvc_error_t result;

	ENTER();
	result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, ctrl,
		!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
		requestWidth, requestHeight, requestMinFps, requestMaxFps
	);
	if (LIKELY(!result)) {
#if LOCAL_DEBUG
		uvc_print_stream_ctrl(ctrl, stderr);
#endif
		uvc_frame_desc_t *frame_desc;
		result = uvc_get_frame_desc(mDeviceHandle, ctrl, &frame_desc);
		if (LIKELY(!result)) {
			frameWidth = frame_desc->wWidth;
			frameHeight = frame_desc->wHeight;
			LOGI("frameSize=(%d,%d)@%s", frameWidth, frameHeight, (!requestMode ? "YUYV" : "MJPEG"));
			pthread_mutex_lock(&preview_mutex);
			if (LIKELY(mPreviewWindow)) {
				ANativeWindow_setBuffersGeometry(mPreviewWindow,
					frameWidth, frameHeight, previewFormat);
			}
			pthread_mutex_unlock(&preview_mutex);
		} else {
			frameWidth = requestWidth;
			frameHeight = requestHeight;
		}
		frameMode = requestMode;
		frameBytes = frameWidth * frameHeight * (!requestMode ? 2 : 4);
		previewBytes = frameWidth * frameHeight * PREVIEW_PIXEL_BYTES;
	} else {
		LOGE("could not negotiate with camera:err=%d", result);
	}
	RETURN(result, int);
}

void UVCPreview::do_preview(uvc_stream_ctrl_t *ctrl) {
	ENTER();

	uvc_frame_t *frame = NULL;
	uvc_frame_t *frame_mjpeg = NULL;
	uvc_error_t result = uvc_start_streaming_bandwidth(
		mDeviceHandle, ctrl, uvc_preview_frame_callback, (void *)this, requestBandwidth, 0);

	if (LIKELY(!result)) {
#if LOCAL_DEBUG
		LOGI("Streaming...");
#endif
		if (frameMode) {
			// MJPEG mode
			for ( ; LIKELY(isRunning()) ; ) {
				frame_mjpeg = waitPreviewFrame();
				if (LIKELY(frame_mjpeg)) {
					frame = get_frame(frame_mjpeg->width * frame_mjpeg->height * 2);
					result = uvc_mjpeg2yuyv(frame_mjpeg, frame);   // MJPEG => yuyv
					recycle_frame(frame_mjpeg);
					if (LIKELY(!result)) {
						frame = draw_preview_one(frame, &mPreviewWindow, uvc_any2rgbx, 4);
						recycle_frame(frame);
					} else {
						recycle_frame(frame);
					}
				}
			}
		} else {
			// yuvyv mode
			for ( ; LIKELY(isRunning()) ; ) {
				frame = waitPreviewFrame();
				if (LIKELY(frame)) {
					frame = draw_preview_one(frame, &mPreviewWindow, uvc_any2rgbx, 4);
					recycle_frame(frame);
				}
			}
		}
#if LOCAL_DEBUG
		LOGI("preview_thread_func:wait for all callbacks complete");
#endif
		uvc_stop_streaming(mDeviceHandle);
#if LOCAL_DEBUG
		LOGI("Streaming finished");
#endif
	} else {
		uvc_perror(result, "failed start_streaming");
	}

	EXIT();
}

static void copyFrame(const uint8_t *src, uint8_t *dest, const int width, int height, const int stride_src, const int stride_dest) {
	const int h8 = height % 8;
	for (int i = 0; i < h8; i++) {
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
	}
	for (int i = 0; i < height; i += 8) {
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
	}
}


// transfer specific frame data to the Surface(ANativeWindow)
int copyToSurface(uvc_frame_t *frame, ANativeWindow **window) {
	// ENTER();
	int result = 0;
	if (LIKELY(*window)) {
		ANativeWindow_Buffer buffer;
		if (LIKELY(ANativeWindow_lock(*window, &buffer, NULL) == 0)) {
			// source = frame data
			const uint8_t *src = (uint8_t *)frame->data;
			const int src_w = frame->width * PREVIEW_PIXEL_BYTES;
			const int src_step = frame->width * PREVIEW_PIXEL_BYTES;
			// destination = Surface(ANativeWindow)
			uint8_t *dest = (uint8_t *)buffer.bits;
			const int dest_w = buffer.width * PREVIEW_PIXEL_BYTES;
			const int dest_step = buffer.stride * PREVIEW_PIXEL_BYTES;
			// use lower transfer bytes
			const int w = src_w < dest_w ? src_w : dest_w;
			// use lower height
			const int h = frame->height < buffer.height ? frame->height : buffer.height;
			// transfer from frame data to the Surface
			copyFrame(src, dest, w, h, src_step, dest_step);
			ANativeWindow_unlockAndPost(*window);
		} else {
			result = -1;
		}
	} else {
		result = -1;
	}
	return result; //RETURN(result, int);
}

// changed to return original frame instead of returning converted frame even if convert_func is not null.
uvc_frame_t *UVCPreview::draw_preview_one(uvc_frame_t *frame, ANativeWindow **window, convFunc_t convert_func, int pixcelBytes) {
	// ENTER();

	int b = 0;
	pthread_mutex_lock(&preview_mutex);
	{
		b = *window != NULL;
	}
	pthread_mutex_unlock(&preview_mutex);
	if (LIKELY(b)) {
		uvc_frame_t *converted;
		if (convert_func) {
			converted = get_frame(frame->width * frame->height * pixcelBytes);
			if LIKELY(converted) {
				b = convert_func(frame, converted);
				if (!b) {
					pthread_mutex_lock(&preview_mutex);
					copyToSurface(converted, window);
					pthread_mutex_unlock(&preview_mutex);
				} else {
					LOGE("failed converting");
				}
				recycle_frame(converted);
			}
		} else {
			pthread_mutex_lock(&preview_mutex);
			copyToSurface(frame, window);
			pthread_mutex_unlock(&preview_mutex);
		}
	}
	return frame; //RETURN(frame, uvc_frame_t *);
}

