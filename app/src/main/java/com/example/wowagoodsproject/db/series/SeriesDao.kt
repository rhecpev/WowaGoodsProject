package com.example.wowagoodsproject.db.series

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Query("SELECT * FROM tb_series ORDER BY seriesDate DESC")
    suspend fun getAll(): List<SeriesEntity>

    @Insert
    suspend fun insert(series: SeriesEntity)

    @Update
    suspend fun update(series: SeriesEntity)

    @Delete
    suspend fun delete(series: SeriesEntity)

    @Query("SELECT * FROM tb_series ORDER BY seriesDate DESC")
    fun getAllFlow(): Flow<List<SeriesEntity>>
}