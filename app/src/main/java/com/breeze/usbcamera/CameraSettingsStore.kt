package com.breeze.usbcamera

import android.content.Context
import android.hardware.usb.UsbDevice
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize

data class CameraSettings(
    val deviceId: Int = -1,
    val vendorId: Int = -1,
    val productId: Int = -1,
    val width: Int = 1280,
    val height: Int = 720,
    val format: CameraRequest.PreviewFormat = CameraRequest.PreviewFormat.FORMAT_MJPEG,
    val maxFps: Int = 30
)

object CameraSettingsStore {
    private const val PREFS = "camera_settings"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_VENDOR_ID = "vendor_id"
    private const val KEY_PRODUCT_ID = "product_id"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_FORMAT = "format"
    private const val KEY_MAX_FPS = "max_fps"
    private const val KEY_PREVIEW_SIZES = "preview_sizes"

    fun load(context: Context): CameraSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CameraSettings(
            deviceId = prefs.getInt(KEY_DEVICE_ID, -1),
            vendorId = prefs.getInt(KEY_VENDOR_ID, -1),
            productId = prefs.getInt(KEY_PRODUCT_ID, -1),
            width = prefs.getInt(KEY_WIDTH, 1280),
            height = prefs.getInt(KEY_HEIGHT, 720),
            format = runCatching {
                CameraRequest.PreviewFormat.valueOf(
                    prefs.getString(KEY_FORMAT, CameraRequest.PreviewFormat.FORMAT_MJPEG.name)
                        ?: CameraRequest.PreviewFormat.FORMAT_MJPEG.name
                )
            }.getOrDefault(CameraRequest.PreviewFormat.FORMAT_MJPEG),
            maxFps = prefs.getInt(KEY_MAX_FPS, 30)
        )
    }

    fun save(context: Context, settings: CameraSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DEVICE_ID, settings.deviceId)
            .putInt(KEY_VENDOR_ID, settings.vendorId)
            .putInt(KEY_PRODUCT_ID, settings.productId)
            .putInt(KEY_WIDTH, settings.width)
            .putInt(KEY_HEIGHT, settings.height)
            .putString(KEY_FORMAT, settings.format.name)
            .putInt(KEY_MAX_FPS, settings.maxFps)
            .apply()
    }

    fun saveDevice(context: Context, device: UsbDevice) {
        val current = load(context)
        save(
            context,
            current.copy(
                deviceId = device.deviceId,
                vendorId = device.vendorId,
                productId = device.productId
            )
        )
    }

    fun savePreviewSizes(context: Context, sizes: List<PreviewSize>) {
        val serialized = sizes.distinctBy { it.width to it.height }
            .joinToString(separator = ",") { "${it.width}x${it.height}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREVIEW_SIZES, serialized)
            .apply()
    }

    fun loadPreviewSizes(context: Context): List<PreviewSize> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREVIEW_SIZES, null)
            ?: return listOf(PreviewSize(1280, 720), PreviewSize(1920, 1080), PreviewSize(640, 480))
        return raw.split(",").mapNotNull { token ->
            val parts = token.split("x")
            val width = parts.getOrNull(0)?.toIntOrNull()
            val height = parts.getOrNull(1)?.toIntOrNull()
            if (width != null && height != null) PreviewSize(width, height) else null
        }.ifEmpty {
            listOf(PreviewSize(1280, 720), PreviewSize(1920, 1080), PreviewSize(640, 480))
        }
    }

    fun labelForDevice(device: UsbDevice): String {
        val productName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            device.productName
        } else {
            null
        }
        val name = productName?.takeIf { it.isNotBlank() } ?: device.deviceName
        return "$name  VID:${device.vendorId} PID:${device.productId}"
    }
}
