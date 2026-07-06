package com.jiangdg.ausbc.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.jiangdg.ausbc.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

class CameraAudioPlayer(
    private val source: UsbCameraAudioSource,
    private val onError: (String) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        audioThread = Thread(::runPlayback, AUDIO_THREAD_NAME).also { it.start() }
    }

    fun stop() {
        running.set(false)
        audioThread?.interrupt()
        audioThread = null
        releaseOutput()
        source.release()
    }

    private fun runPlayback() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        try {
            source.init()
            val outputChannel = outputChannelConfig(source.channelCount())
            val minBufferSize = AudioTrack.getMinBufferSize(
                source.sampleRate(),
                outputChannel,
                source.audioFormat()
            )
            if (minBufferSize <= 0) {
                throw IllegalStateException("invalid audio output buffer")
            }
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                source.sampleRate(),
                outputChannel,
                source.audioFormat(),
                minBufferSize,
                AudioTrack.MODE_STREAM
            )
            source.start()
            audioTrack?.play()
            while (running.get() && source.isRecording()) {
                val pcm = source.read() ?: continue
                audioTrack?.write(pcm, 0, pcm.size)
            }
        } catch (e: RuntimeException) {
            val message = e.localizedMessage ?: "USB camera audio playback failed"
            Logger.e(TAG, message, e)
            onError(message)
        } finally {
            running.set(false)
            releaseOutput()
            source.release()
        }
    }

    private fun releaseOutput() {
        try {
            audioTrack?.stop()
        } catch (e: IllegalStateException) {
            Logger.w(TAG, "audio output was not playing", e)
        }
        audioTrack?.release()
        audioTrack = null
    }

    private fun outputChannelConfig(channelCount: Int): Int {
        return if (channelCount == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
    }

    private companion object {
        private const val TAG = "CameraAudioPlayer"
        private const val AUDIO_THREAD_NAME = "usb-camera-audio"
    }
}
