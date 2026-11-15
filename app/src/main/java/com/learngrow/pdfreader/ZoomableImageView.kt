package com.learngrow.pdfreader

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private val lastTouch = PointF()
    var currentScale = 1f
        private set
    private val minScale = 1f
    private val maxScale = 5f
    var onSingleTap: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val targetScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
                val factor = targetScale / currentScale
                currentScale = targetScale
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = matrix
                return true
            }
        })

    private val tapDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTap?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom()
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setZoom(scale: Float) {
        matrix.reset()
        currentScale = scale.coerceIn(minScale, maxScale)
        matrix.postScale(currentScale, currentScale)
        imageMatrix = matrix
    }

    fun resetZoom() {
        matrix.reset()
        currentScale = 1f
        imageMatrix = matrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastTouch.set(event.x, event.y)
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && currentScale > 1f) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y
                    matrix.postTranslate(dx, dy)
                    imageMatrix = matrix
                }
                lastTouch.set(event.x, event.y)
            }
        }
        return true
    }
}
