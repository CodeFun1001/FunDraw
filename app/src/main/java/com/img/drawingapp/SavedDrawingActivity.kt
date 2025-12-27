package com.img.drawingapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.io.File

class SavedDrawingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DrawingsAdapter
    private lateinit var db: AppDatabase
    private val drawingsList = mutableListOf<DrawingEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_drawings)

        db = AppDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.recyclerView)
        adapter = DrawingsAdapter(this, drawingsList, object : DrawingsAdapter.OnDrawingClickListener {
            override fun onClick(drawing: DrawingEntity) {
                val intent = Intent(this@SavedDrawingsActivity, MainActivity::class.java)
                intent.putExtra("drawing_path", drawing.filePath)
                intent.putExtra("drawing_id", drawing.id)
                startActivity(intent)
            }

            override fun onLongClick(drawing: DrawingEntity) {
                showOptionsDialog(drawing)
            }
        })

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabNewDrawing).setOnClickListener {
            startNewDrawing()
        }

        loadDrawings()
    }

    private fun loadDrawings() {
        lifecycleScope.launch {
            db.drawingDao().getAllDrawings().collect { list ->
                drawingsList.clear()
                drawingsList.addAll(list)
                adapter.updateData(drawingsList)
            }
        }
    }

    private fun startNewDrawing() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun showOptionsDialog(drawing: DrawingEntity) {
        val options = arrayOf("Delete", "Rename", "Share")
        AlertDialog.Builder(this)
            .setTitle(drawing.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deleteDrawing(drawing)
                    1 -> renameDrawing(drawing)
                    2 -> shareDrawing(drawing)
                }
            }
            .show()
    }

    private fun deleteDrawing(drawing: DrawingEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Drawing?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    File(drawing.filePath).delete()
                    db.drawingDao().delete(drawing)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameDrawing(drawing: DrawingEntity) {
        val editText = EditText(this)
        editText.setText(drawing.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Drawing")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val newName = editText.text.toString()
                val newFile = File(File(drawing.filePath).parent, "$newName.png")
                if (File(drawing.filePath).renameTo(newFile)) {
                    drawing.filePath = newFile.absolutePath
                    drawing.name = newName
                    lifecycleScope.launch { db.drawingDao().update(drawing) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareDrawing(drawing: DrawingEntity) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(drawing.filePath))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }
}