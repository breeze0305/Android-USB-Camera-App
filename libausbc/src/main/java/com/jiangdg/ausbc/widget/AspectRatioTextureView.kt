package com.jiangdg.ausbc.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import com.jiangdg.ausbc.utils.Logger
import kotlin.math.abs

class AspectRatioTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), IAspectRatio {

    private var aspectRatio = 0.0

    override fun setAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            Logger.w(TAG, "ignore invalid aspect ratio size: ${width}x${height}")
            return
        }
        post {
            setAspectRatio(width.toDouble() / height.toDouble())
        }
    }

    override fun getSurfaceWidth(): Int = measuredWidth

    override fun getSurfaceHeight(): Int = measuredHeight

    override fun getSurface(): Surface? {
        val texture = surfaceTexture ?: return null
        return try {
            Surface(texture)
        } catch (e: RuntimeException) {
            Logger.w(TAG, "create preview surface failed", e)
            null
        }
    }

    override fun postUITask(task: () -> Unit) {
        post(task)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectRatio <= 0.0 || measuredWidth <= 0 || measuredHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom
        val contentWidth = (measuredWidth - horizontalPadding).coerceAtLeast(0)
        val contentHeight = (measuredHeight - verticalPadding).coerceAtLeast(0)
        if (contentWidth == 0 || contentHeight == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var targetWidth = contentWidth
        var targetHeight = contentHeight
        val currentRatio = contentWidth.toDouble() / contentHeight.toDouble()
        if (abs(aspectRatio / currentRatio - 1.0) > 0.01) {
            if (aspectRatio > currentRatio) {
                targetHeight = (contentWidth / aspectRatio).toInt()
            } else {
                targetWidth = (contentHeight * aspectRatio).toInt()
            }
        }

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(targetWidth + horizontalPadding, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(targetHeight + verticalPadding, MeasureSpec.EXACTLY)
        )
    }

    private fun setAspectRatio(newRatio: Double) {
        if (newRatio <= 0.0 || aspectRatio == newRatio) return
        aspectRatio = newRatio
        Logger.i(TAG, "AspectRatio = $aspectRatio")
        requestLayout()
    }

    private companion object {
        private const val TAG = "AspectRatioTextureView"
    }
}
