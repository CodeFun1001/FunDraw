package com.img.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var mCanvasBitmap: Bitmap? = null
    private var mDrawPaint: Paint? = null
    private var mCanvasPaint: Paint? = null
    private var mBrushSize: Float = 0.toFloat()
    private var color = Color.BLACK
    private var isEraserOn = false
    private var canvas: Canvas? = null
    private var backgroundBitmap: Bitmap? = null


    private var currentPath: CustomPath? = null
    private val paths = mutableListOf<CustomPath>()
    private val undoPaths = mutableListOf<CustomPath>()

    init
    {
        setUpDrawing()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun setUpDrawing()
    {
        mDrawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isDither = true
        }
        mCanvasPaint = Paint(Paint.DITHER_FLAG)
    }

    fun setBackgroundImage(bitmap: Bitmap)
    {
        backgroundBitmap = bitmap
        invalidate()
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)
    {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(mCanvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas)
    {
        super.onDraw(canvas)

        backgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        mCanvasBitmap?.let{ canvas.drawBitmap(it, 0f, 0f, mCanvasPaint) }

        currentPath?.let{
            mDrawPaint?.strokeWidth = it.brushThickness
            mDrawPaint?.color = it.color
            mDrawPaint?.xfermode =
                if (it.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            canvas.drawPath(it, mDrawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean
    {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = CustomPath(color, mBrushSize, isEraserOn)
                currentPath?.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.let {
                    paths.add(it)
                    undoPaths.clear()
                    redrawOnBitmap()
                }
                currentPath = null
            }
        }
        invalidate()
        return true
    }

    fun setSizeForBrush(newBrushSize: Float)
    {
        mBrushSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            newBrushSize,
            resources.displayMetrics
        )
        mDrawPaint?.strokeWidth = mBrushSize
    }

    fun setColor(newColor: Int)
    {
        color = newColor
        mDrawPaint?.color = color
    }

    fun enableEraser(){ isEraserOn = true }

    fun disableEraser(){ isEraserOn = false }

    private fun redrawOnBitmap()
    {
        mCanvasBitmap?.eraseColor(Color.TRANSPARENT)
        canvas?.let{
            c ->
            for(path in paths)
            {
                mDrawPaint?.apply{
                    strokeWidth = path.brushThickness
                    color = path.color
                    xfermode = if (path.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                else null
                }
                c.drawPath(path, mDrawPaint!!)
            }
        }
    }

    fun undo()
    {
        if(paths.isNotEmpty())
        {
            val lastPath = paths.removeAt(paths.size - 1)
            undoPaths.add(lastPath)
            redrawOnBitmap()
            invalidate()
        }
    }

    fun redo()
    {
        if(undoPaths.isNotEmpty())
        {
            val path = undoPaths.removeAt(undoPaths.size - 1)
            paths.add(path)
            redrawOnBitmap()
            invalidate()
        }
    }

    internal inner class CustomPath(
        var color: Int,
        var brushThickness: Float,
        var isEraser: Boolean = false
    ) : Path()
}
