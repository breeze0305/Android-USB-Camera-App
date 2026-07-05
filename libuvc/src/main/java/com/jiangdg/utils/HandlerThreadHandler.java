package com.jiangdg.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HandlerThreadHandler extends Handler {
    private static final String DEFAULT_THREAD_NAME = "HandlerThreadHandler";

    public static HandlerThreadHandler createHandler() {
        return createHandler(DEFAULT_THREAD_NAME);
    }

    public static HandlerThreadHandler createHandler(final String name) {
        final HandlerThread thread = startThread(name);
        return new HandlerThreadHandler(thread.getLooper());
    }

    public static HandlerThreadHandler createHandler(@Nullable final Callback callback) {
        return createHandler(DEFAULT_THREAD_NAME, callback);
    }

    public static HandlerThreadHandler createHandler(final String name, @Nullable final Callback callback) {
        final HandlerThread thread = startThread(name);
        return new HandlerThreadHandler(thread.getLooper(), callback);
    }

    private static HandlerThread startThread(final String name) {
        final HandlerThread thread = new HandlerThread(
                name == null || name.trim().isEmpty() ? DEFAULT_THREAD_NAME : name
        );
        thread.start();
        return thread;
    }

    private HandlerThreadHandler(@NonNull final Looper looper) {
        super(looper);
    }

    private HandlerThreadHandler(@NonNull final Looper looper, @Nullable final Callback callback) {
        super(looper, callback);
    }
}
