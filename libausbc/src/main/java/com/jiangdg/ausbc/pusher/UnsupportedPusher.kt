package com.jiangdg.ausbc.pusher

import android.content.Context
import com.jiangdg.ausbc.pusher.callback.IStateCallback
import com.jiangdg.ausbc.pusher.config.AusbcConfig

class UnsupportedPusher(
    private val name: String = "Pusher"
) : IPusher {
    private var callback: IStateCallback? = null

    override fun init(context: Context?, ausbcConfig: AusbcConfig?, callback: IStateCallback?) {
        this.callback = callback
        report("Streaming is disabled: no $name implementation is configured.")
    }

    override fun start(url: String?) {
        report("Streaming is disabled: no $name implementation is configured.")
    }

    override fun stop() = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun reconnect() {
        report("Reconnect ignored because streaming is disabled.")
    }

    override fun reconnectUrl(url: String?) {
        report("Reconnect ignored because streaming is disabled.")
    }

    override fun pushStream(type: Int, data: ByteArray?, size: Int, pts: Long) = Unit

    override fun destroy() {
        callback = null
    }

    override fun isPushing(): Boolean = false

    private fun report(message: String) {
        callback?.onPushState(STATE_UNSUPPORTED, message)
    }

    companion object {
        const val STATE_UNSUPPORTED = -1
    }
}
