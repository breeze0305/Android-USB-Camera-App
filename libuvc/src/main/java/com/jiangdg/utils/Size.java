package com.jiangdg.utils;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Locale;

public class Size implements Parcelable {
    private static final int FRAME_INTERVAL_NONE = -1;
    private static final int FRAME_INTERVAL_RANGE = 0;
    private static final float UVC_100NS_UNITS_PER_SECOND = 10_000_000f;

    public int type;
    public int frame_type;
    public int index;
    public int width;
    public int height;
    public int frameIntervalType;
    public int frameIntervalIndex;
    public int[] intervals;
    public float[] fps;
    private String frameRates;

    public Size(final int type, final int frameType, final int index, final int width, final int height) {
        this.type = type;
        this.frame_type = frameType;
        this.index = index;
        this.width = width;
        this.height = height;
        this.frameIntervalType = FRAME_INTERVAL_NONE;
        this.frameIntervalIndex = 0;
        this.intervals = null;
        updateFrameRate();
    }

    public Size(
            final int type,
            final int frameType,
            final int index,
            final int width,
            final int height,
            final int minInterval,
            final int maxInterval,
            final int intervalStep) {
        this.type = type;
        this.frame_type = frameType;
        this.index = index;
        this.width = width;
        this.height = height;
        this.frameIntervalType = FRAME_INTERVAL_RANGE;
        this.frameIntervalIndex = 0;
        this.intervals = new int[]{minInterval, maxInterval, intervalStep};
        updateFrameRate();
    }

    public Size(
            final int type,
            final int frameType,
            final int index,
            final int width,
            final int height,
            final int[] intervals) {
        this.type = type;
        this.frame_type = frameType;
        this.index = index;
        this.width = width;
        this.height = height;
        setIntervals(intervals);
        updateFrameRate();
    }

    public Size(final Size other) {
        set(other);
    }

    private Size(final Parcel source) {
        type = source.readInt();
        frame_type = source.readInt();
        index = source.readInt();
        width = source.readInt();
        height = source.readInt();
        frameIntervalType = source.readInt();
        frameIntervalIndex = source.readInt();
        if (frameIntervalType == FRAME_INTERVAL_NONE) {
            intervals = null;
        } else {
            final int count = frameIntervalType == FRAME_INTERVAL_RANGE ? 3 : frameIntervalType;
            intervals = new int[Math.max(count, 0)];
            source.readIntArray(intervals);
        }
        updateFrameRate();
    }

    public Size set(final Size other) {
        if (other == null) {
            return this;
        }
        type = other.type;
        frame_type = other.frame_type;
        index = other.index;
        width = other.width;
        height = other.height;
        frameIntervalType = other.frameIntervalType;
        frameIntervalIndex = other.frameIntervalIndex;
        intervals = other.intervals != null ? Arrays.copyOf(other.intervals, other.intervals.length) : null;
        updateFrameRate();
        return this;
    }

    public float getCurrentFrameRate() throws IllegalStateException {
        if (fps != null && frameIntervalIndex >= 0 && frameIntervalIndex < fps.length) {
            return fps[frameIntervalIndex];
        }
        throw new IllegalStateException("unknown frame rate or not ready");
    }

    public void setCurrentFrameRate(final float frameRate) {
        frameIntervalIndex = -1;
        if (fps == null) {
            return;
        }
        for (int i = 0; i < fps.length; i++) {
            if (fps[i] <= frameRate) {
                frameIntervalIndex = i;
                return;
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(type);
        dest.writeInt(frame_type);
        dest.writeInt(index);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeInt(frameIntervalType);
        dest.writeInt(frameIntervalIndex);
        if (intervals != null) {
            dest.writeIntArray(intervals);
        }
    }

    public void updateFrameRate() {
        if (frameIntervalType > 0) {
            fps = buildDiscreteFps(intervals, frameIntervalType);
        } else if (frameIntervalType == FRAME_INTERVAL_RANGE) {
            fps = buildRangeFps(intervals);
        } else {
            fps = null;
        }

        final int count = fps != null ? fps.length : 0;
        if (count == 0) {
            frameIntervalIndex = -1;
        } else if (frameIntervalIndex < 0 || frameIntervalIndex >= count) {
            frameIntervalIndex = 0;
        }
        frameRates = formatFrameRates(fps);
    }

    @Override
    public String toString() {
        float frameRate = 0f;
        try {
            frameRate = getCurrentFrameRate();
        } catch (final IllegalStateException ignored) {
            // Keep unknown frame rates printable for diagnostics.
        }
        return String.format(
                Locale.US,
                "Size(%dx%d@%4.1f,type:%d,frame:%d,index:%d,%s)",
                width,
                height,
                frameRate,
                type,
                frame_type,
                index,
                frameRates
        );
    }

    private void setIntervals(final int[] source) {
        if (source == null || source.length == 0) {
            frameIntervalType = FRAME_INTERVAL_NONE;
            frameIntervalIndex = 0;
            intervals = null;
            return;
        }
        frameIntervalType = source.length;
        frameIntervalIndex = 0;
        intervals = Arrays.copyOf(source, source.length);
    }

    private static float[] buildDiscreteFps(final int[] intervals, final int count) {
        if (intervals == null || intervals.length < count) {
            return null;
        }
        final float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            if (intervals[i] <= 0) {
                return null;
            }
            result[i] = UVC_100NS_UNITS_PER_SECOND / intervals[i];
        }
        return result;
    }

    private static float[] buildRangeFps(final int[] intervals) {
        if (intervals == null || intervals.length < 3) {
            return null;
        }
        final int min = Math.min(intervals[0], intervals[1]);
        final int max = Math.max(intervals[0], intervals[1]);
        final int step = intervals[2];
        if (min <= 0 || max <= 0) {
            return null;
        }

        if (step > 0) {
            final int count = ((max - min) / step) + 1;
            final float[] result = new float[count];
            for (int i = 0, value = min; i < count; i++, value += step) {
                result[i] = UVC_100NS_UNITS_PER_SECOND / value;
            }
            return result;
        }

        final float minFps = UVC_100NS_UNITS_PER_SECOND / max;
        final float maxFps = UVC_100NS_UNITS_PER_SECOND / min;
        final int count = Math.max(1, (int) Math.floor(maxFps - minFps) + 1);
        final float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = minFps + i;
        }
        return result;
    }

    private static String formatFrameRates(final float[] values) {
        if (values == null || values.length == 0) {
            return "[]";
        }
        final StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            builder.append(String.format(Locale.US, "%4.1f", values[i]));
            if (i < values.length - 1) {
                builder.append(",");
            }
        }
        builder.append("]");
        return builder.toString();
    }

    public static final Creator<Size> CREATOR = new Creator<Size>() {
        @Override
        public Size createFromParcel(final Parcel source) {
            return new Size(source);
        }

        @Override
        public Size[] newArray(final int size) {
            return new Size[size];
        }
    };
}
