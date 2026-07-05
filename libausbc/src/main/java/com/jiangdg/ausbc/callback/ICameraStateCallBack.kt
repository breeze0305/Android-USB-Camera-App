package com.jiangdg.ausbc.callback

import com.jiangdg.ausbc.MultiCameraClient

interface ICameraStateCallBack {
    fun onCameraState(self: MultiCameraClient.ICamera, code: State, msg: String? = null)

    enum class State {
        OPENED,
        CLOSED,
        ERROR
    }
}
