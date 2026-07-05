package com.jiangdg.ausbc.utils

import android.content.Context
import java.io.IOException

object MediaUtils {
    private const val TAG = "MediaUtils"

    fun readRawTextFile(context: Context, rawId: Int): String {
        return try {
            context.resources.openRawResource(rawId).bufferedReader().use { reader ->
                buildString {
                    reader.forEachLine { line ->
                        append(line)
                        append('\n')
                    }
                }
            }
        } catch (e: IOException) {
            Logger.e(TAG, "open raw file failed", e)
            ""
        } catch (e: RuntimeException) {
            Logger.e(TAG, "read raw file failed", e)
            ""
        }
    }
}
