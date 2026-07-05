package com.breeze.usbcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraActivity
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPlayCallBack
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import kotlin.math.max

class MainActivity : CameraActivity() {
    private enum class DisplayMode {
        CONTAIN,
        STRETCH,
        COVER
    }

    private lateinit var rootView: FrameLayout
    private lateinit var cameraView: AspectRatioTextureView
    private lateinit var cameraContainer: FrameLayout
    private lateinit var topControls: View
    private lateinit var statusText: TextView
    private lateinit var displayModeButton: ImageButton
    private lateinit var resetViewButton: ImageButton
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private var settings = CameraSettings()
    private var skipNextResumeApply = true
    private var isMicPlaying = false
    private var pendingStartMic = false
    private var displayMode = DisplayMode.CONTAIN
    private var userZoom = 1f
    private var userPanX = 0f
    private var userPanY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = CameraSettingsStore.load(this)
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        showControlsTemporarily()
        if (skipNextResumeApply) {
            skipNextResumeApply = false
            return
        }
        val latestSettings = CameraSettingsStore.load(this)
        if (latestSettings != settings) {
            applySettings(latestSettings)
        }
    }

    override fun onPause() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        super.onPause()
    }

    override fun getRootView(layoutInflater: LayoutInflater): View {
        val root = layoutInflater.inflate(R.layout.activity_usb_camera, null)
        rootView = root.findViewById(R.id.rootView)
        cameraView = AspectRatioTextureView(this)
        cameraContainer = root.findViewById(R.id.cameraContainer)
        topControls = root.findViewById(R.id.topControls)
        statusText = root.findViewById(R.id.statusText)
        displayModeButton = root.findViewById(R.id.displayModeButton)
        resetViewButton = root.findViewById(R.id.resetViewButton)
        return root
    }

    override fun initView() {
        super.initView()
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldZoom = userZoom
                val newZoom = (userZoom * detector.scaleFactor).coerceIn(1f, 6f)
                val zoomDelta = newZoom / oldZoom
                val focusOffsetX = detector.focusX - cameraView.width / 2f
                val focusOffsetY = detector.focusY - cameraView.height / 2f
                userPanX = (userPanX - focusOffsetX) * zoomDelta + focusOffsetX
                userPanY = (userPanY - focusOffsetY) * zoomDelta + focusOffsetY
                userZoom = newZoom
                applyPreviewTransform()
                return true
            }
        })
        val revealControlsOnTouch = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                showControlsTemporarily()
            }
            false
        }
        rootView.setOnTouchListener(revealControlsOnTouch)
        cameraContainer.setOnTouchListener(revealControlsOnTouch)
        (cameraView as View).setOnTouchListener { _, event ->
            showControlsTemporarily()
            scaleGestureDetector.onTouchEvent(event)
            handlePreviewTouch(event)
            true
        }
        cameraView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncPreviewLayout()
            cameraView.postDelayed({ syncPreviewLayout() }, 120L)
        }
        displayModeButton.setOnClickListener {
            showControlsTemporarily()
            displayMode = when (displayMode) {
                DisplayMode.CONTAIN -> DisplayMode.STRETCH
                DisplayMode.STRETCH -> DisplayMode.COVER
                DisplayMode.COVER -> DisplayMode.CONTAIN
            }
            updateDisplayModeButtonIcon()
            applyPreviewTransform()
        }
        resetViewButton.setOnClickListener {
            showControlsTemporarily()
            resetPreviewView()
        }
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            controlsHandler.removeCallbacks(hideControlsRunnable)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        updateDisplayModeButtonIcon()
        applyPreviewTransform()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetPreviewPan()
        cameraView.post { syncPreviewLayout() }
        cameraView.postDelayed({ syncPreviewLayout() }, 120L)
        cameraView.postDelayed({ syncPreviewLayout() }, 320L)
    }

    override fun getCameraView(): IAspectRatio = cameraView

    override fun getCameraViewContainer(): ViewGroup = cameraContainer

    override fun getGravity(): Int = Gravity.CENTER

    override fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(settings.width)
            .setPreviewHeight(settings.height)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(RotateType.ANGLE_0)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_AUTO)
            .setPreviewFormat(settings.format)
            .setPreviewFpsRange(1, settings.maxFps)
            .setAspectRatioShow(false)
            .setCaptureRawImage(false)
            .setRawPreviewData(false)
            .create()
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        runOnUiThread {
            when (code) {
                ICameraStateCallBack.State.OPENED -> {
                    val size = getCurrentPreviewSize()
                    persistAvailableOptions(self.getUsbDevice())
                    statusText.text = "已開啟 ${CameraSettingsStore.labelForDevice(self.getUsbDevice())} ${size?.width ?: settings.width}x${size?.height ?: settings.height}"
                    applyPreviewTransform()
                    if (pendingStartMic || settings.playAudio) {
                        pendingStartMic = false
                        startMicPlayback()
                    }
                }
                ICameraStateCallBack.State.CLOSED -> {
                    statusText.text = "Camera 已關閉"
                    isMicPlaying = false
                }
                ICameraStateCallBack.State.ERROR -> {
                    statusText.text = "Camera 錯誤: ${msg ?: "未知錯誤"}"
                    isMicPlaying = false
                }
            }
        }
    }

    private fun applySettings(newSettings: CameraSettings) {
        val previousSettings = settings
        settings = newSettings

        val selectedDevice = findSelectedDevice(newSettings)
        val currentDevice = getCurrentCamera()?.getUsbDevice()
        if (selectedDevice != null && currentDevice?.deviceId != selectedDevice.deviceId) {
            if (newSettings.playAudio) {
                pendingStartMic = true
                stopMicPlayback()
            }
            statusText.text = "切換到 ${CameraSettingsStore.labelForDevice(selectedDevice)}"
            switchCamera(selectedDevice)
            return
        }

        val camera = getCurrentCamera()
        val request = camera?.getCameraRequest()
        val needsReopen = request != null &&
            (request.previewFormat != newSettings.format || request.previewMaxFps != newSettings.maxFps)
        val needsResolutionUpdate = request != null &&
            (request.previewWidth != newSettings.width || request.previewHeight != newSettings.height)

        when {
            needsReopen -> reopenCameraAfterSettingsChange(newSettings.playAudio)
            needsResolutionUpdate -> updateResolution(newSettings.width, newSettings.height)
        }

        if (newSettings.playAudio != previousSettings.playAudio) {
            if (newSettings.playAudio) startMicPlayback() else stopMicPlayback()
        }
    }

    private fun reopenCameraAfterSettingsChange(shouldRestartAudio: Boolean) {
        val camera = getCurrentCamera() ?: return
        if (shouldRestartAudio) {
            pendingStartMic = true
            stopMicPlayback()
        }
        camera.closeCamera()
        cameraView.postDelayed({
            camera.openCamera(cameraView, getCameraRequest())
            camera.setCameraStateCallBack(this)
        }, 700)
    }

    private fun persistAvailableOptions(device: UsbDevice) {
        CameraSettingsStore.saveDevice(this, device)
        getAllPreviewSizes()
            ?.distinctBy { it.width to it.height }
            ?.sortedWith(compareByDescending<PreviewSize> { it.width * it.height }.thenByDescending { it.width })
            ?.let { CameraSettingsStore.savePreviewSizes(this, it) }
    }

    private fun findSelectedDevice(settings: CameraSettings): UsbDevice? {
        val devices = getDeviceList().orEmpty()
        return devices.firstOrNull { it.deviceId == settings.deviceId }
            ?: devices.firstOrNull {
                it.vendorId == settings.vendorId && it.productId == settings.productId
            }
    }

    private fun startMicPlayback() {
        if (isMicPlaying) return
        if (!hasAudioPermission()) {
            requestRuntimePermissions()
            Toast.makeText(this, "需要麥克風權限才能播放 camera 聲音", Toast.LENGTH_SHORT).show()
            return
        }
        startPlayMic(object : IPlayCallBack {
            override fun onBegin() {
                runOnUiThread { isMicPlaying = true }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    isMicPlaying = false
                    Toast.makeText(this@MainActivity, "音訊無法播放: $error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onComplete() {
                runOnUiThread { isMicPlaying = false }
            }
        })
    }

    private fun stopMicPlayback() {
        stopPlayMic()
        isMicPlaying = false
    }

    private fun handlePreviewTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleGestureDetector.isInProgress) {
                    userPanX += event.x - lastTouchX
                    userPanY += event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    applyPreviewTransform()
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
        }
    }

    private fun resetPreviewView() {
        displayMode = DisplayMode.CONTAIN
        userZoom = 1f
        resetPreviewPan()
        updateDisplayModeButtonIcon()
        applyPreviewTransform()
    }

    private fun resetPreviewPan() {
        userPanX = 0f
        userPanY = 0f
    }

    private fun updateDisplayModeButtonIcon() {
        val icon = when (displayMode) {
            DisplayMode.CONTAIN -> R.drawable.ic_preview_contain
            DisplayMode.STRETCH -> R.drawable.ic_preview_stretch
            DisplayMode.COVER -> R.drawable.ic_preview_cover
        }
        val label = when (displayMode) {
            DisplayMode.CONTAIN -> "初始顯示模式"
            DisplayMode.STRETCH -> "強制全螢幕拉伸模式"
            DisplayMode.COVER -> "等比全螢幕覆蓋模式"
        }
        displayModeButton.setImageResource(icon)
        displayModeButton.contentDescription = label
    }

    private fun applyPreviewTransform() {
        if (!::cameraView.isInitialized || cameraView.width == 0 || cameraView.height == 0) return

        val viewWidth = cameraView.width.toFloat()
        val viewHeight = cameraView.height.toFloat()
        val previewSize = getCurrentPreviewSize()
        val previewWidth = (previewSize?.width ?: settings.width).coerceAtLeast(1).toFloat()
        val previewHeight = (previewSize?.height ?: settings.height).coerceAtLeast(1).toFloat()
        val previewAspect = previewWidth / previewHeight
        val viewAspect = viewWidth / viewHeight

        var baseScaleX = 1f
        var baseScaleY = 1f
        when (displayMode) {
            DisplayMode.CONTAIN -> {
                if (previewAspect > viewAspect) {
                    baseScaleY = viewAspect / previewAspect
                } else {
                    baseScaleX = previewAspect / viewAspect
                }
            }
            DisplayMode.STRETCH -> Unit
            DisplayMode.COVER -> {
                if (previewAspect > viewAspect) {
                    baseScaleX = previewAspect / viewAspect
                } else {
                    baseScaleY = viewAspect / previewAspect
                }
            }
        }

        val finalScaleX = baseScaleX * userZoom
        val finalScaleY = baseScaleY * userZoom
        val maxPanX = max(0f, (viewWidth * finalScaleX - viewWidth) / 2f)
        val maxPanY = max(0f, (viewHeight * finalScaleY - viewHeight) / 2f)
        userPanX = userPanX.coerceIn(-maxPanX, maxPanX)
        userPanY = userPanY.coerceIn(-maxPanY, maxPanY)

        val matrix = Matrix()
        matrix.setScale(finalScaleX, finalScaleY, viewWidth / 2f, viewHeight / 2f)
        matrix.postTranslate(userPanX, userPanY)
        cameraView.setTransform(matrix)
    }

    private fun syncPreviewLayout() {
        if (!::cameraView.isInitialized || cameraView.width == 0 || cameraView.height == 0) return
        getCurrentCamera()?.setRenderSize(cameraView.width, cameraView.height)
        applyPreviewTransform()
    }

    private fun showControlsTemporarily() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        topControls.animate().cancel()
        topControls.visibility = View.VISIBLE
        topControls.alpha = 1f
        controlsHandler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun hideControls() {
        topControls.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                topControls.visibility = View.GONE
                topControls.alpha = 1f
            }
            .start()
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 42)
        }
    }

    private fun hasAudioPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
