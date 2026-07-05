package com.jiangdg.utils;

import android.os.Build;

public final class BuildCheck {
    private BuildCheck() {
    }

    public static boolean isAndroid5() {
        return atLeast(Build.VERSION_CODES.LOLLIPOP);
    }

    public static boolean isLollipop() {
        return atLeast(Build.VERSION_CODES.LOLLIPOP);
    }

    public static boolean isMarshmallow() {
        return atLeast(Build.VERSION_CODES.M);
    }

    private static boolean atLeast(final int apiLevel) {
        return Build.VERSION.SDK_INT >= apiLevel;
    }
}
