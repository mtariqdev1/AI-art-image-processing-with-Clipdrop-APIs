package com.example.clipdropapicheck.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MaskView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val maskPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val maskBitmap: Bitmap
    private val maskCanvas: Canvas
    private val pathsHistory = mutableListOf<Path>()
    private var currentPath: Path? = null
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f

    private val undonePaths = mutableListOf<Path>()

    init {
        maskPaint.color = Color.BLACK
        maskPaint.style = Paint.Style.STROKE
        maskPaint.strokeWidth = 100f // Adjust the stroke width as needed
        maskPaint.strokeCap = Paint.Cap.ROUND // Set round line caps

        maskBitmap = Bitmap.createBitmap(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
            Bitmap.Config.ARGB_8888
        )
        maskCanvas = Canvas(maskBitmap)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(maskBitmap, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startNewPath()
                currentPath?.moveTo(x, y)
                lastTouchX = x
                lastTouchY = y
                maskCanvas.drawPoint(x, y, maskPaint)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(x - lastTouchX)
                val dy = Math.abs(y - lastTouchY)
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    currentPath?.quadTo(lastTouchX, lastTouchY, (x + lastTouchX) / 2, (y + lastTouchY) / 2)
                    lastTouchX = x
                    lastTouchY = y
                    maskCanvas.drawPath(currentPath!!, maskPaint)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                saveCurrentPath()
            }
        }
        return true
    }

    fun undo() {
        if (pathsHistory.isNotEmpty()) {
            undonePaths.add(pathsHistory.removeAt(pathsHistory.size - 1))
            redrawPaths()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            pathsHistory.add(undonePaths.removeAt(undonePaths.size - 1))
            redrawPaths()
        }
    }

    private fun redrawPaths() {
        maskBitmap.eraseColor(Color.TRANSPARENT)
        for (path in pathsHistory) {
            maskCanvas.drawPath(path, maskPaint)
        }
        invalidate()
    }

    private fun startNewPath() {
        currentPath = Path()
    }

    private fun saveCurrentPath() {
        currentPath?.let {
            pathsHistory.add(it)
            currentPath = null
        }
    }



    fun clearMask() {
        pathsHistory.clear()
        currentPath = null
        maskBitmap.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    companion object {
        private const val TOUCH_TOLERANCE = 4f
    }
}