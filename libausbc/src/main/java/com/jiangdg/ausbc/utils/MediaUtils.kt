package com.jiangdg.ausbc.utils

import android.content.Context
import android.content.res.Resources
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
        } catch (e: Resources.NotFoundException) {
            Logger.e(TAG, "raw file resource not found", e)
            ""
        }
    }
}
