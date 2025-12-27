package com.img.drawingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DrawingsAdapter(
    private val context: Context,
    private var drawings: List<DrawingEntity>,
    private val listener: OnDrawingClickListener
) : RecyclerView.Adapter<DrawingsAdapter.DrawingViewHolder>() {

    interface OnDrawingClickListener {
        fun onClick(drawing: DrawingEntity)
        fun onLongClick(drawing: DrawingEntity)
    }

    inner class DrawingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
        val name: TextView = itemView.findViewById(R.id.drawingName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_drawing, parent, false)
        return DrawingViewHolder(view)
    }

    override fun getItemCount() = drawings.size

    override fun onBindViewHolder(holder: DrawingViewHolder, position: Int) {
        val drawing = drawings[position]
        holder.name.text = drawing.name

        val bitmap = BitmapFactory.decodeFile(drawing.filePath)
        holder.thumbnail.setImageBitmap(Bitmap.createScaledBitmap(bitmap, 200, 200, true))

        holder.itemView.setOnClickListener { listener.onClick(drawing) }
        holder.itemView.setOnLongClickListener {
            listener.onLongClick(drawing)
            true
        }
    }

    fun updateData(newDrawings: List<DrawingEntity>) {
        drawings = newDrawings
        notifyDataSetChanged()
    }
}