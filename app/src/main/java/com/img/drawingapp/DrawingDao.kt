package com.img.drawingapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {

    @Query("SELECT * FROM drawings ORDER BY timestamp DESC")
    fun getAllDrawings(): Flow<List<DrawingEntity>>

    @Insert
    suspend fun insert(drawing: DrawingEntity): Long

    @Update
    suspend fun update(drawing: DrawingEntity)

    @Delete
    suspend fun delete(drawing: DrawingEntity)
}
