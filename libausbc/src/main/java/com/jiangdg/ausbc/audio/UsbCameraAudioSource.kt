package com.jiangdg.ausbc.audio

import android.media.AudioFormat
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.uac.UACAudio
import com.jiangdg.usb.USBMonitor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class UsbCameraAudioSource(private val ctrlBlock: USBMonitor.UsbControlBlock) {
    private val pcmQueue = ArrayBlockingQueue<ByteArray>(MAX_QUEUE_SIZE)
    private var audio: UACAudio? = null

    val isAvailable: Boolean
        get() = UACAudio.isAvailable()

    fun init() {
        if (!UACAudio.isAvailable()) {
            throw IllegalStateException("UAC native audio is unavailable")
        }
        val uacAudio = UACAudio()
        uacAudio.setAudioCallBack { data ->
            if (!pcmQueue.offer(data)) {
                pcmQueue.poll()
                pcmQueue.offer(data)
            }
        }
        uacAudio.init(ctrlBlock)
        if (uacAudio.audioStatus == UACAudio.AudioStatus.ERROR) {
            uacAudio.release()
            throw IllegalStateException("USB camera audio init failed")
        }
        audio = uacAudio
    }

    fun start() {
        audio?.startRecording()
    }

    fun stop() {
        audio?.stopRecording()
        pcmQueue.clear()
    }

    fun release() {
        stop()
        audio?.release()
        audio = null
    }

    fun read(timeoutMs: Long = 100): ByteArray? {
        return try {
            pcmQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    fun isRecording(): Boolean = audio?.isRecording == true

    fun sampleRate(): Int = audio?.sampleRate?.takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE

    fun channelCount(): Int = audio?.channelCount?.takeIf { it > 0 } ?: DEFAULT_CHANNEL_COUNT

    fun audioFormat(): Int {
        return when (audio?.bitResolution) {
            8 -> AudioFormat.ENCODING_PCM_8BIT
            16 -> AudioFormat.ENCODING_PCM_16BIT
            else -> {
                Logger.w(TAG, "unknown UAC bit resolution; using PCM 16-bit")
                AudioFormat.ENCODING_PCM_16BIT
            }
        }
    }

    private companion object {
        private const val TAG = "UsbCameraAudioSource"
        private const val MAX_QUEUE_SIZE = 12
        private const val DEFAULT_SAMPLE_RATE = 8000
        private const val DEFAULT_CHANNEL_COUNT = 1
    }
}
