package com.img.drawingapp
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawings")
data class DrawingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var name: String,
    var filePath: String,
    var timestamp: Long = System.currentTimeMillis()
)

