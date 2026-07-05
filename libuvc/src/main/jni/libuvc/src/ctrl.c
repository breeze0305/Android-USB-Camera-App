/*********************************************************************
 * add some functions unsupported on original libuvc library
 * and fixed some issues
 * Copyright (C) 2014-2015 saki@serenegiant All rights reserved.
 *********************************************************************/
/*********************************************************************
 * Software License Agreement (BSD License)
 *
 *  Copyright (C) 2010-2012 Ken Tossell
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of the author nor other contributors may be
 *     used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *********************************************************************/
/**
 * @defgroup ctrl Video capture and processing controls
 * @brief Functions for manipulating device settings and stream parameters
 *
 * The `uvc_get_*` and `uvc_set_*` functions are used to read and write the settings associated
 * with the device's input, processing and output units.
 */

#include "libuvc/libuvc.h"
#include "libuvc/libuvc_internal.h"

static const int REQ_TYPE_GET = 0xa1;

#define CTRL_TIMEOUT_MILLIS 0

/***** INTERFACE CONTROLS *****/
/** VC Request Error Code Control (UVC 4.2.1.2) */ // XXX added saki
uvc_error_t uvc_vc_get_error_code(uvc_device_handle_t *devh,
		uvc_vc_error_code_control_t *error_code, enum uvc_req_code req_code) {
	uint8_t error_char = 0;
	uvc_error_t ret = UVC_SUCCESS;

	ret = libusb_control_transfer(devh->usb_devh, REQ_TYPE_GET, req_code,
			UVC_VC_REQUEST_ERROR_CODE_CONTROL << 8,
			devh->info->ctrl_if.bInterfaceNumber,	// XXX saki
			&error_char, sizeof(error_char), CTRL_TIMEOUT_MILLIS);

	if (LIKELY(ret == 1)) {
		*error_code = error_char;
		return UVC_SUCCESS;
	} else {
		return ret;
	}
}

/** VS Request Error Code Control */ // XXX added saki
uvc_error_t uvc_vs_get_error_code(uvc_device_handle_t *devh,
		uvc_vs_error_code_control_t *error_code, enum uvc_req_code req_code) {
	uint8_t error_char = 0;
	uvc_error_t ret = UVC_SUCCESS;

#if 0 // This code may cause hang-up on some combinations of device and camera and temporary disabled.
	ret = libusb_control_transfer(devh->usb_devh, REQ_TYPE_GET, req_code,
			UVC_VS_STREAM_ERROR_CODE_CONTROL << 8,
			devh->info->stream_ifs->bInterfaceNumber,	// XXX is this OK?
			&error_char, sizeof(error_char), CTRL_TIMEOUT_MILLIS);

	if (LIKELY(ret == 1)) {
		*error_code = error_char;
		return UVC_SUCCESS;
	} else {
		return ret;
	}
#else
	return ret;
#endif
}
