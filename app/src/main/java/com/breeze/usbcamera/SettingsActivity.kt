package com.breeze.usbcamera

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize

class SettingsActivity : AppCompatActivity() {
    private lateinit var cameraSpinner: Spinner
    private lateinit var resolutionSpinner: Spinner
    private lateinit var formatSpinner: Spinner
    private lateinit var fpsText: TextView
    private lateinit var audioSwitch: Switch

    private val fpsOptions = intArrayOf(15, 24, 30, 45, 60, 120)
    private var devices = emptyList<UsbDevice>()
    private var previewSizes = emptyList<PreviewSize>()
    private var settings = CameraSettings()
    private var bindingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        cameraSpinner = findViewById(R.id.cameraSpinner)
        resolutionSpinner = findViewById(R.id.resolutionSpinner)
        formatSpinner = findViewById(R.id.formatSpinner)
        fpsText = findViewById(R.id.fpsText)
        audioSwitch = findViewById(R.id.audioSwitch)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        settings = CameraSettingsStore.load(this)
        loadDeviceList()
        previewSizes = CameraSettingsStore.loadPreviewSizes(this)
        bindUi()
    }

    private fun loadDeviceList() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        devices = usbManager.deviceList.values.sortedWith(
            compareBy<UsbDevice> { it.vendorId }.thenBy { it.productId }.thenBy { it.deviceId }
        )
    }

    private fun bindUi() {
        bindingUi = true
        bindCameraSpinner()
        bindResolutionSpinner()
        bindFormatSpinner()
        bindFpsSeek()
        bindAudioSwitch()
        bindingUi = false
    }

    private fun bindCameraSpinner() {
        val labels = if (devices.isEmpty()) {
            listOf("未偵測到 USB camera")
        } else {
            devices.map { CameraSettingsStore.labelForDevice(it) }
        }
        cameraSpinner.adapter = spinnerAdapter(labels)
        val selected = devices.indexOfFirst { device ->
            device.deviceId == settings.deviceId ||
                (device.vendorId == settings.vendorId && device.productId == settings.productId)
        }
        if (selected >= 0) cameraSpinner.setSelection(selected)
        cameraSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingUi || devices.isEmpty()) return
                val device = devices[position.coerceIn(devices.indices)]
                settings = settings.copy(
                    deviceId = device.deviceId,
                    vendorId = device.vendorId,
                    productId = device.productId
                )
                CameraSettingsStore.save(this@SettingsActivity, settings)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun bindResolutionSpinner() {
        val orderedSizes = previewSizes.sortedWith(
            compareByDescending<PreviewSize> { it.width * it.height }.thenByDescending { it.width }
        )
        previewSizes = orderedSizes
        resolutionSpinner.adapter = spinnerAdapter(orderedSizes.map { "${it.width} x ${it.height}" })
        val selected = orderedSizes.indexOfFirst { it.width == settings.width && it.height == settings.height }
        if (selected >= 0) resolutionSpinner.setSelection(selected)
        resolutionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingUi || previewSizes.isEmpty()) return
                val size = previewSizes[position.coerceIn(previewSizes.indices)]
                settings = settings.copy(width = size.width, height = size.height)
                CameraSettingsStore.save(this@SettingsActivity, settings)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun bindFormatSpinner() {
        val formats = listOf("MJPEG（較高 FPS）", "YUYV（較低延遲）")
        formatSpinner.adapter = spinnerAdapter(formats)
        formatSpinner.setSelection(if (settings.format == CameraRequest.PreviewFormat.FORMAT_MJPEG) 0 else 1)
        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingUi) return
                settings = settings.copy(
                    format = if (position == 0) {
                        CameraRequest.PreviewFormat.FORMAT_MJPEG
                    } else {
                        CameraRequest.PreviewFormat.FORMAT_YUYV
                    }
                )
                CameraSettingsStore.save(this@SettingsActivity, settings)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun bindFpsSeek() {
        val fpsSeek = findViewById<SeekBar>(R.id.fpsSeek)
        val progress = fpsOptions.indexOf(settings.maxFps).takeIf { it >= 0 } ?: 2
        fpsSeek.progress = progress
        updateFpsText(fpsOptions[progress])
        fpsSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fps = fpsOptions[progress.coerceIn(fpsOptions.indices)]
                updateFpsText(fps)
                if (bindingUi) return
                settings = settings.copy(maxFps = fps)
                CameraSettingsStore.save(this@SettingsActivity, settings)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindAudioSwitch() {
        audioSwitch.isChecked = settings.playAudio
        audioSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (bindingUi) return@setOnCheckedChangeListener
            settings = settings.copy(playAudio = isChecked)
            CameraSettingsStore.save(this@SettingsActivity, settings)
        }
    }

    private fun updateFpsText(fps: Int) {
        fpsText.text = "FPS 上限：$fps"
    }

    private fun spinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }
}
