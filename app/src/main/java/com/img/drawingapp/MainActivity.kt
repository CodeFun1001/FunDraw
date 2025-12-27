package com.img.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.min
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity()
{
    private var drawingView: DrawingView? = null
    private var currentBrushSize = 25
    private var currentColor = Color.BLACK
    private var cameraImageUri: Uri? = null
    private var customProgressDialog: Dialog? = null
    private var drawingId: Int? = null

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent())
        { uri ->
            uri ?: return@registerForActivityResult

            val bitmap =
                MediaStore.Images.Media.getBitmap(contentResolver, uri)

            drawingView?.post {
                val fittedBitmap = scaleBitmapToFit(
                    bitmap,
                    drawingView!!.width,
                    drawingView!!.height
                )
                drawingView?.setBackgroundImage(fittedBitmap)
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture())
        { success ->
            if(success)
            {
                cameraImageUri?.let{ uri ->
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

                    drawingView?.post{
                        val fittedBitmap = scaleBitmapToFit(
                            bitmap,
                            drawingView!!.width,
                            drawingView!!.height
                        )
                        drawingView?.setBackgroundImage(fittedBitmap)
                    }
                }
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission())
        {   granted ->
            if(granted)
            {
                openCamera()
            }
            else
            {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(currentBrushSize.toFloat())
        drawingView?.setColor(currentColor)

        val brush: ImageButton = findViewById(R.id.brush)
        brush.setOnClickListener {
            brushSizeSelectorDialog()
            drawingView?.disableEraser()
        }
        findViewById<ImageButton>(R.id.colorPalette).setOnClickListener {
            showColorPaletteDialog()
            drawingView?.disableEraser()
        }
        findViewById<ImageButton>(R.id.undo).setOnClickListener {
            drawingView?.undo()
        }
        findViewById<ImageButton>(R.id.redo).setOnClickListener {
            drawingView?.redo()
        }
        findViewById<ImageButton>(R.id.eraser).setOnClickListener {
            drawingView?.enableEraser()
        }

        val gallery: ImageButton = findViewById<ImageButton>(R.id.gallery)
        gallery.setOnClickListener {
            showImageSourceDialog()
        }

        val save: ImageButton = findViewById<ImageButton>(R.id.save)
        save.setOnClickListener {
            showSaveDialog()
        }

        drawingId = intent.getIntExtra("drawing_id", -1).takeIf { it != -1 }

        val drawingPath = intent.getStringExtra("drawing_path")
        drawingPath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            drawingView?.post {
                drawingView?.setBackgroundImage(scaleBitmapToFit(bitmap, drawingView!!.width, drawingView!!.height))
            }
        }
    }

    override fun onDestroy()
    {
        super.onDestroy()
        cancelProgressDialog()
    }

    private fun brushSizeSelectorDialog()
    {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)

        brushDialog.window?.apply{
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val seekBar = brushDialog.findViewById<SeekBar>(R.id.seekBarBrushSize)
        val preview = brushDialog.findViewById<View>(R.id.brushPreview)

        seekBar.progress = currentBrushSize
        updatePreviewSize(preview, currentBrushSize)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBrushSize = progress
                drawingView?.setSizeForBrush(progress.toFloat())
                updatePreviewSize(preview, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brushDialog.show()
    }

    private fun updatePreviewSize(preview: View, progress: Int)
    {
        val minDp = 6
        val sizeDp = minDp + progress / 2

        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            resources.displayMetrics
        ).toInt()

        val params = preview.layoutParams as FrameLayout.LayoutParams
        params.width = sizePx
        params.height = sizePx
        preview.layoutParams = params
    }

    private fun showColorPaletteDialog()
    {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_color_palette)

        dialog.window?.apply{
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val grid = dialog.findViewById<GridLayout>(R.id.colorGrid)

        for(i in 0 until grid.childCount)
        {
            val view = grid.getChildAt(i)

            if(view is View && view.id != R.id.addColor)
            {
                view.setOnClickListener{
                    val color = (view.backgroundTintList)?.defaultColor
                    if (color != null) {
                        currentColor = color
                        drawingView?.setColor(currentColor)
                        dialog.dismiss()
                    }
                }
            }
            if(view.id == R.id.addColor)
            {
                view.setOnClickListener{
                    dialog.dismiss()
                    openAdvancedColorPicker()
                }
            }
        }

        dialog.show()
    }

    private fun openAdvancedColorPicker()
    {
        ColorPickerDialog.Builder(this)
            .setTitle("Pick a Color 🎨")
            .setPreferenceName("MyColorPicker")
            .setPositiveButton(
                "Choose",
                ColorEnvelopeListener { envelope, _ ->
                    currentColor = envelope.color
                    drawingView?.setColor(currentColor)
                })
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .attachBrightnessSlideBar(true)
            .attachAlphaSlideBar(true)
            .show()
    }

    private suspend fun saveBitmapToFile(bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DrawingApp")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Drawing_${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap?)
    {
        withContext(Dispatchers.IO){
            if(bitmap == null)
            {
                runOnUiThread{
                    cancelProgressDialog()
                    Toast.makeText(
                        this@MainActivity,
                        "Error: Could not create image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext
            }

            val filename = "DrawingApp_${System.currentTimeMillis()}.png"

            val values = ContentValues().apply{
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/DrawingApp"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = contentResolver
            val imageUri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            imageUri?.let{ uri ->
                try{
                    resolver.openOutputStream(uri).use{ outputStream ->
                        if(outputStream != null)
                        {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    runOnUiThread{
                        cancelProgressDialog()
                        Toast.makeText(
                            this@MainActivity,
                            "Saved to Gallery",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                catch(e: Exception)
                {
                    e.printStackTrace()
                    runOnUiThread{
                        cancelProgressDialog()
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to save image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } ?: run {
                runOnUiThread{
                    cancelProgressDialog()
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to create image file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun showSaveDialog()
    {
        val editText = EditText(this).apply{
            hint = "Enter drawing name"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Save Drawing")
            .setView(editText)
            .setPositiveButton("Save"){ _, _ ->
                val name = editText.text.toString().trim()

                if(name.isEmpty())
                {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    saveDrawingWithName(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun saveDrawingWithName(name: String)
    {
        lifecycleScope.launch{
            val bitmap = getBitmapFromView(findViewById(R.id.fl_drawing_container))
            val file = saveBitmapToFile(bitmap)

            if(file != null)
            {
                val db = AppDatabase.getDatabase(this@MainActivity)
                if(drawingId != null)
                {
                    val drawing = db.drawingDao()
                        .getAllDrawings()
                        .first()
                        .find { it.id == drawingId }

                    drawing?.let{
                        it.name = name
                        it.filePath = file.absolutePath
                        db.drawingDao().update(it)
                    }
                }
                else
                {
                    val newDrawing = DrawingEntity(
                        name = name,
                        filePath = file.absolutePath
                    )
                    db.drawingDao().insert(newDrawing)
                }

                saveBitmapToGallery(bitmap)

                runOnUiThread{
                    Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun getBitmapFromView(view: View): Bitmap
    {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null)
        {
            bgDrawable.draw(canvas)
        }
        else
        {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private fun openCamera()
    {
        cameraImageUri = createImageUri()
        cameraLauncher.launch(cameraImageUri!!)
    }

    private fun createImageUri(): Uri
    {
        val imageFile = File.createTempFile(
            "camera_image_",
            ".jpg",
            cacheDir
        )

        return FileProvider.getUriForFile(
            this,
            "com.img.drawingapp.fileprovider",
            imageFile
        )
    }

    fun scaleBitmapToFit(bitmap: Bitmap, viewWidth: Int, viewHeight: Int): Bitmap
    {
        val ratio = min(
            viewWidth.toFloat() / bitmap.width,
            viewHeight.toFloat() / bitmap.height
        )

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        val result = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val left = (viewWidth - newWidth) / 2f
        val top = (viewHeight - newHeight) / 2f

        canvas.drawBitmap(scaledBitmap, left, top, null)
        return result
    }

    private fun showImageSourceDialog()
    {
        val options = arrayOf("Camera 📸", "Gallery 🖼️")

        AlertDialog.Builder(this)
            .setTitle("Choose Image")
            .setItems(options) { _, which ->
                when(which)
                {
                    0 -> {
                        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        {
                            openCamera()
                        }
                        else
                        {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }

                    1 -> {
                        galleryLauncher.launch("image/*")
                    }
                }
            }
            .show()
    }

    private fun cancelProgressDialog()
    {
        customProgressDialog?.let{
            if(it.isShowing)
            {
                it.dismiss()
            }
        }
        customProgressDialog = null
    }
}