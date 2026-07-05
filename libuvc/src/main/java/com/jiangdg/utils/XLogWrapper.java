package com.jiangdg.utils;

import android.app.Application;
import android.util.Log;

import com.jiangdg.uvc.UVCCamera;

public final class XLogWrapper {
    private static final String DEFAULT_TAG = "USBCamera";

    private XLogWrapper() {
    }

    public static void init(Application application, String folderPath) {
        // Kept for API compatibility. This project intentionally does not write camera logs to files.
    }

    public static void v(String tag, String msg) {
        if (UVCCamera.DEBUG) {
            Log.v(cleanTag(tag), safeMessage(msg));
        }
    }

    public static void i(String tag, String msg) {
        if (UVCCamera.DEBUG) {
            Log.i(cleanTag(tag), safeMessage(msg));
        }
    }

    public static void d(String tag, String msg) {
        if (UVCCamera.DEBUG) {
            Log.d(cleanTag(tag), safeMessage(msg));
        }
    }

    public static void w(String tag, String msg) {
        Log.w(cleanTag(tag), safeMessage(msg));
    }

    public static void w(String tag, String msg, Throwable throwable) {
        Log.w(cleanTag(tag), safeMessage(msg), throwable);
    }

    public static void w(String tag, Throwable throwable) {
        Log.w(cleanTag(tag), "", throwable);
    }

    public static void e(String tag, String msg) {
        Log.e(cleanTag(tag), safeMessage(msg));
    }

    public static void e(String tag, String msg, Throwable throwable) {
        Log.e(cleanTag(tag), safeMessage(msg), throwable);
    }

    private static String cleanTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return DEFAULT_TAG;
        }
        return tag.length() > 23 ? tag.substring(0, 23) : tag;
    }

    private static String safeMessage(String msg) {
        return msg == null ? "" : msg;
    }
}
