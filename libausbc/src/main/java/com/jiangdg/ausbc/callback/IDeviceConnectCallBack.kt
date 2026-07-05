package com.jiangdg.ausbc.callback

import android.hardware.usb.UsbDevice
import com.jiangdg.usb.USBMonitor

interface IDeviceConnectCallBack {
    fun onAttachDev(device: UsbDevice?)
    fun onDetachDec(device: UsbDevice?)
    fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock? = null)
    fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock? = null)
    fun onCancelDev(device: UsbDevice?)
}
