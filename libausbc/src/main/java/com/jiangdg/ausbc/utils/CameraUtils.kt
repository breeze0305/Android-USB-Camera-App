package com.jiangdg.ausbc.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import androidx.core.content.ContextCompat
import com.jiangdg.ausbc.R
import com.jiangdg.usb.DeviceFilter

object CameraUtils {
    fun isUsbCamera(device: UsbDevice?): Boolean {
        return when (device?.deviceClass) {
            UsbConstants.USB_CLASS_VIDEO -> true
            UsbConstants.USB_CLASS_MISC ->
                (0 until device.interfaceCount).any { index ->
                    device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_VIDEO
                }
            else -> false
        }
    }

    fun isFilterDevice(context: Context?, usbDevice: UsbDevice?): Boolean {
        if (context == null || usbDevice == null) return false
        return DeviceFilter.getDeviceFilters(context, R.xml.default_device_filter).any { devFilter ->
            devFilter.mProductId == usbDevice.productId &&
                devFilter.mVendorId == usbDevice.vendorId
        }
    }

    fun isCameraContainsMic(device: UsbDevice?): Boolean {
        device ?: return false
        return (0 until device.interfaceCount).any { index ->
            device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_AUDIO
        }
    }

    fun hasCameraPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
