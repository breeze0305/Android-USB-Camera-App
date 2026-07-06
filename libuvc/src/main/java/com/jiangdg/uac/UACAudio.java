package com.jiangdg.uac;

import android.text.TextUtils;

import com.jiangdg.usb.USBMonitor;
import com.jiangdg.utils.XLogWrapper;

public class UACAudio {
    private static final String TAG = "UACAudio";
    private static final String DEFAULT_USBFS = "/dev/bus/usb";
    private static final boolean NATIVE_AVAILABLE;

    private UACAudioCallBack audioCallBack;
    private AudioStatus status = AudioStatus.RELEASED;
    private long mNativePtr;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("UACAudio");
            loaded = true;
        } catch (final UnsatisfiedLinkError | SecurityException e) {
            XLogWrapper.w(TAG, "UAC native audio is unavailable", e);
        }
        NATIVE_AVAILABLE = loaded;
    }

    public static boolean isAvailable() {
        return NATIVE_AVAILABLE;
    }

    public synchronized void init(final USBMonitor.UsbControlBlock ctrlBlock) {
        if (!NATIVE_AVAILABLE || ctrlBlock == null) {
            status = AudioStatus.ERROR;
            return;
        }
        int result = -1;
        try {
            result = nativeInit(
                    ctrlBlock.getVenderId(),
                    ctrlBlock.getProductId(),
                    ctrlBlock.getBusNum(),
                    ctrlBlock.getDevNum(),
                    ctrlBlock.getFileDescriptor(),
                    getUsbFsName(ctrlBlock)
            );
        } catch (final RuntimeException | UnsatisfiedLinkError e) {
            XLogWrapper.w(TAG, "init UAC audio failed", e);
        }
        status = result < 0 ? AudioStatus.ERROR : AudioStatus.CREATED;
    }

    public synchronized void startRecording() {
        if (!NATIVE_AVAILABLE || status != AudioStatus.CREATED && status != AudioStatus.STOPPED) {
            status = AudioStatus.ERROR;
            return;
        }
        status = nativeStartRecord(mNativePtr) < 0 ? AudioStatus.ERROR : AudioStatus.RUNNING;
    }

    public synchronized void stopRecording() {
        if (!NATIVE_AVAILABLE || status != AudioStatus.RUNNING) {
            return;
        }
        if (nativeStopRecord(mNativePtr) >= 0) {
            status = AudioStatus.STOPPED;
        }
    }

    public synchronized void release() {
        if (NATIVE_AVAILABLE) {
            nativeRelease(mNativePtr);
        }
        status = AudioStatus.RELEASED;
    }

    public int getSampleRate() {
        return NATIVE_AVAILABLE ? nativeGetSampleRate(mNativePtr) : -1;
    }

    public int getChannelCount() {
        return NATIVE_AVAILABLE ? nativeGetChannelCount(mNativePtr) : -1;
    }

    public int getBitResolution() {
        return NATIVE_AVAILABLE ? nativeGetBitResolution(mNativePtr) : -1;
    }

    public AudioStatus getAudioStatus() {
        return status;
    }

    public boolean isRecording() {
        return NATIVE_AVAILABLE && nativeGetRecordingState(mNativePtr);
    }

    public void setAudioCallBack(final UACAudioCallBack callBack) {
        audioCallBack = callBack;
    }

    public void pcmData(final byte[] data) {
        final UACAudioCallBack callBack = audioCallBack;
        if (callBack != null) {
            callBack.pcmData(data);
        }
    }

    private String getUsbFsName(final USBMonitor.UsbControlBlock ctrlBlock) {
        final String name = ctrlBlock.getDeviceName();
        final String[] parts = !TextUtils.isEmpty(name) ? name.split("/") : null;
        if (parts == null || parts.length <= 2) {
            return DEFAULT_USBFS;
        }
        final StringBuilder builder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length - 2; i++) {
            builder.append("/").append(parts[i]);
        }
        final String usbFs = builder.toString();
        return TextUtils.isEmpty(usbFs) ? DEFAULT_USBFS : usbFs;
    }

    private native int nativeInit(int vid, int pid, int busnum, int devaddr, int fd, String usbfs);

    private native void nativeRelease(long id);

    private native int nativeStartRecord(long id);

    private native int nativeStopRecord(long id);

    private native boolean nativeGetRecordingState(long id);

    private native int nativeGetSampleRate(long id);

    private native int nativeGetChannelCount(long id);

    private native int nativeGetBitResolution(long id);

    public enum AudioStatus {
        CREATED,
        RUNNING,
        STOPPED,
        RELEASED,
        ERROR
    }
}
