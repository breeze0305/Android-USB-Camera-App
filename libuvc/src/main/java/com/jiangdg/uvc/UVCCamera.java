package com.jiangdg.uvc;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.jiangdg.usb.USBMonitor;
import com.jiangdg.usb.USBMonitor.UsbControlBlock;
import com.jiangdg.utils.Size;
import com.jiangdg.utils.XLogWrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UVCCamera {
    public static boolean DEBUG = false;

    public static final int FRAME_FORMAT_YUYV = 0;
    public static final int FRAME_FORMAT_MJPEG = 1;

    public static final int DEFAULT_PREVIEW_MIN_FPS = 1;
    public static final int DEFAULT_PREVIEW_MAX_FPS = 31;
    public static final float DEFAULT_BANDWIDTH = 1.0f;
    public static final int DEFAULT_PREVIEW_MODE = FRAME_FORMAT_MJPEG;

    public static final int PIXEL_FORMAT_RAW = 0;
    public static final int PIXEL_FORMAT_YUV = 1;
    public static final int PIXEL_FORMAT_RGB565 = 2;
    public static final int PIXEL_FORMAT_RGBX = 3;
    public static final int PIXEL_FORMAT_YUV420SP = 4;
    public static final int PIXEL_FORMAT_NV21 = 5;

    public static final int STATUS_CLASS_CONTROL = 0x10;
    public static final int STATUS_CLASS_CONTROL_CAMERA = 0x11;
    public static final int STATUS_CLASS_CONTROL_PROCESSING = 0x12;

    public static final int STATUS_ATTRIBUTE_VALUE_CHANGE = 0x00;
    public static final int STATUS_ATTRIBUTE_INFO_CHANGE = 0x01;
    public static final int STATUS_ATTRIBUTE_FAILURE_CHANGE = 0x02;
    public static final int STATUS_ATTRIBUTE_UNKNOWN = 0xff;

    private static final String TAG = UVCCamera.class.getSimpleName();
    private static final String DEFAULT_USBFS = "/dev/bus/usb";
    private static final int NATIVE_FORMAT_YUYV = 4;
    private static final int NATIVE_FORMAT_MJPEG = 6;
    private static boolean loaded;

    static {
        if (!loaded) {
            System.loadLibrary("jpeg-turbo1500");
            System.loadLibrary("usb100");
            System.loadLibrary("uvc");
            System.loadLibrary("UVCCamera");
            loaded = true;
        }
    }

    private UsbControlBlock mCtrlBlock;
    protected int mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
    protected int mCurrentWidth = 640;
    protected int mCurrentHeight = 480;
    protected float mCurrentBandwidthFactor = DEFAULT_BANDWIDTH;
    protected String mSupportedSize;
    protected List<Size> mCurrentSizeList;
    protected long mNativePtr;

    public UVCCamera() {
        mNativePtr = nativeCreate();
    }

    public synchronized void open(final UsbControlBlock ctrlBlock) {
        if (ctrlBlock == null) {
            throw new IllegalArgumentException("UsbControlBlock is null");
        }

        close();
        final UsbControlBlock clonedBlock;
        try {
            clonedBlock = ctrlBlock.clone();
        } catch (final CloneNotSupportedException e) {
            throw new UnsupportedOperationException("open failed: could not clone control block", e);
        }

        mCtrlBlock = clonedBlock;
        final int result;
        try {
            result = nativeConnect(
                    mNativePtr,
                    clonedBlock.getVenderId(),
                    clonedBlock.getProductId(),
                    clonedBlock.getFileDescriptor(),
                    clonedBlock.getBusNum(),
                    clonedBlock.getDevNum(),
                    getUSBFSName(clonedBlock)
            );
        } catch (final RuntimeException e) {
            mCtrlBlock = null;
            clonedBlock.close();
            throw new UnsupportedOperationException("open failed while connecting native camera", e);
        }

        if (result != 0) {
            mCtrlBlock = null;
            clonedBlock.close();
            throw new UnsupportedOperationException("open failed: result=" + result);
        }

        mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
        mSupportedSize = nativeGetSupportedSize(mNativePtr);
        applyInitialPreviewSize();
        final int setResult = nativeSetPreviewSize(
                mNativePtr,
                mCurrentWidth,
                mCurrentHeight,
                DEFAULT_PREVIEW_MIN_FPS,
                DEFAULT_PREVIEW_MAX_FPS,
                DEFAULT_PREVIEW_MODE,
                DEFAULT_BANDWIDTH
        );
        if (setResult != 0) {
            close();
            throw new IllegalArgumentException("Failed to set initial preview size: result=" + setResult);
        }
    }

    public void setStatusCallback(final IStatusCallback callback) {
        if (mNativePtr != 0) {
            nativeSetStatusCallback(mNativePtr, callback);
        }
    }

    public void setButtonCallback(final IButtonCallback callback) {
        if (mNativePtr != 0) {
            nativeSetButtonCallback(mNativePtr, callback);
        }
    }

    public synchronized void close() {
        stopPreview();
        if (mNativePtr != 0) {
            nativeRelease(mNativePtr);
        }
        if (mCtrlBlock != null) {
            mCtrlBlock.close();
            mCtrlBlock = null;
        }
        mCurrentFrameFormat = -1;
        mCurrentBandwidthFactor = 0f;
        mSupportedSize = null;
        mCurrentSizeList = null;
    }

    public UsbDevice getDevice() {
        return mCtrlBlock != null ? mCtrlBlock.getDevice() : null;
    }

    public String getDeviceName() {
        return mCtrlBlock != null ? mCtrlBlock.getDeviceName() : null;
    }

    public UsbControlBlock getUsbControlBlock() {
        return mCtrlBlock;
    }

    public synchronized String getSupportedSize() {
        if (!TextUtils.isEmpty(mSupportedSize)) {
            return mSupportedSize;
        }
        if (mNativePtr == 0) {
            return "";
        }
        mSupportedSize = nativeGetSupportedSize(mNativePtr);
        return mSupportedSize != null ? mSupportedSize : "";
    }

    public Size getPreviewSize() {
        final List<Size> sizes = getSupportedSizeList();
        for (final Size size : sizes) {
            if (size.width == mCurrentWidth && size.height == mCurrentHeight) {
                return size;
            }
        }
        return null;
    }

    public void setPreviewSize(final int width, final int height) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                mCurrentFrameFormat, mCurrentBandwidthFactor);
    }

    public void setPreviewSize(final int width, final int height, final int frameFormat) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                frameFormat, mCurrentBandwidthFactor);
    }

    public void setPreviewSize(
            final int width,
            final int height,
            final int frameFormat,
            final float bandwidth) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                frameFormat, bandwidth);
    }

    public void setPreviewSize(
            final int width,
            final int height,
            final int minFps,
            final int maxFps,
            final int frameFormat,
            final float bandwidthFactor) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid preview size");
        }
        if (mNativePtr == 0 || mCtrlBlock == null) {
            return;
        }

        final int result = nativeSetPreviewSize(
                mNativePtr,
                width,
                height,
                minFps,
                maxFps,
                frameFormat,
                bandwidthFactor
        );
        if (result != 0) {
            throw new IllegalArgumentException("Failed to set preview size: result=" + result);
        }

        mCurrentFrameFormat = frameFormat;
        mCurrentWidth = width;
        mCurrentHeight = height;
        mCurrentBandwidthFactor = bandwidthFactor;
    }

    public List<Size> getSupportedSizeList() {
        if (mCurrentFrameFormat < 0) {
            mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
        }
        return getSupportedSize(nativeFormatForFrameFormat(mCurrentFrameFormat), getSupportedSize());
    }

    public List<Size> getSupportedSizeList(final int frameFormat) {
        return getSupportedSize(nativeFormatForFrameFormat(frameFormat), getSupportedSize());
    }

    public List<Size> getSupportedSize(final int type, final String supportedSize) {
        final List<Size> result = new ArrayList<>();
        if (TextUtils.isEmpty(supportedSize)) {
            return result;
        }

        try {
            final JSONArray formats = new JSONObject(supportedSize).getJSONArray("formats");
            for (int i = 0; i < formats.length(); i++) {
                final JSONObject format = formats.getJSONObject(i);
                final int formatType = format.optInt("type", Integer.MIN_VALUE);
                if (formatType == Integer.MIN_VALUE || (type != -1 && formatType != type)) {
                    continue;
                }
                addSizes(format, formatType, result);
            }
        } catch (final JSONException e) {
            XLogWrapper.w(TAG, "Failed to parse supported camera sizes", e);
        }
        return result;
    }

    public synchronized void setPreviewDisplay(final SurfaceHolder holder) {
        setPreviewDisplay(holder != null ? holder.getSurface() : null);
    }

    public synchronized void setPreviewTexture(final SurfaceTexture texture) {
        if (texture == null) {
            setPreviewDisplay((Surface) null);
            return;
        }

        final Surface surface = new Surface(texture);
        try {
            setPreviewDisplay(surface);
        } finally {
            surface.release();
        }
    }

    public synchronized void setPreviewDisplay(final Surface surface) {
        if (mNativePtr != 0) {
            nativeSetPreviewDisplay(mNativePtr, surface);
        }
    }

    public synchronized void startPreview() {
        if (mCtrlBlock != null && mNativePtr != 0) {
            nativeStartPreview(mNativePtr);
        }
    }

    public synchronized void stopPreview() {
        if (mCtrlBlock != null && mNativePtr != 0) {
            nativeStopPreview(mNativePtr);
        }
    }

    public synchronized void destroy() {
        close();
        if (mNativePtr != 0) {
            nativeDestroy(mNativePtr);
            mNativePtr = 0;
        }
    }

    private void applyInitialPreviewSize() {
        final List<Size> supportedSizes = getSupportedSizeList();
        if (!supportedSizes.isEmpty()) {
            mCurrentWidth = supportedSizes.get(0).width;
            mCurrentHeight = supportedSizes.get(0).height;
        }
    }

    private static int nativeFormatForFrameFormat(final int frameFormat) {
        return frameFormat > 0 ? NATIVE_FORMAT_MJPEG : NATIVE_FORMAT_YUYV;
    }

    private static void addSizes(
            final JSONObject format,
            final int formatType,
            final List<Size> target) throws JSONException {
        final JSONArray sizes = format.optJSONArray("size");
        if (sizes == null) {
            return;
        }

        for (int i = 0; i < sizes.length(); i++) {
            final String rawSize = sizes.optString(i, "");
            final Size parsedSize = parseSize(formatType, i, rawSize);
            if (parsedSize != null) {
                target.add(parsedSize);
            }
        }
    }

    private static Size parseSize(final int formatType, final int index, final String rawSize) {
        final String[] parts = rawSize.toLowerCase(Locale.US).split("x");
        if (parts.length != 2) {
            XLogWrapper.w(TAG, "Skip malformed camera size entry");
            return null;
        }

        try {
            final int width = Integer.parseInt(parts[0].trim());
            final int height = Integer.parseInt(parts[1].trim());
            if (width <= 0 || height <= 0) {
                XLogWrapper.w(TAG, "Skip invalid camera size entry");
                return null;
            }
            return new Size(formatType, 0, index, width, height);
        } catch (final NumberFormatException e) {
            XLogWrapper.w(TAG, "Skip malformed camera size entry", e);
            return null;
        }
    }

    private String getUSBFSName(final UsbControlBlock ctrlBlock) {
        final String deviceName = ctrlBlock.getDeviceName();
        if (!TextUtils.isEmpty(deviceName)) {
            final String[] parts = deviceName.split("/");
            if (parts.length > 2) {
                final StringBuilder builder = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length - 2; i++) {
                    builder.append('/').append(parts[i]);
                }
                final String usbFs = builder.toString();
                if (!TextUtils.isEmpty(usbFs)) {
                    return usbFs;
                }
            }
        }
        XLogWrapper.w(TAG, "failed to get USBFS path, using default path");
        return DEFAULT_USBFS;
    }

    private final native long nativeCreate();

    private final native void nativeDestroy(final long idCamera);

    private final native int nativeConnect(
            long idCamera,
            int venderId,
            int productId,
            int fileDescriptor,
            int busNum,
            int devAddr,
            String usbfs);

    private static final native int nativeRelease(final long idCamera);

    private static final native int nativeSetStatusCallback(final long nativePtr, final IStatusCallback callback);

    private static final native int nativeSetButtonCallback(final long nativePtr, final IButtonCallback callback);

    private static final native int nativeSetPreviewSize(
            final long idCamera,
            final int width,
            final int height,
            final int minFps,
            final int maxFps,
            final int mode,
            final float bandwidth);

    private static final native String nativeGetSupportedSize(final long idCamera);

    private static final native int nativeStartPreview(final long idCamera);

    private static final native int nativeStopPreview(final long idCamera);

    private static final native int nativeSetPreviewDisplay(final long idCamera, final Surface surface);
}
