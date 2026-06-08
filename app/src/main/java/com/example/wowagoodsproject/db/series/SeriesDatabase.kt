package com.example.wowagoodsproject.db.series

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SeriesEntity::class], version = 2)
abstract class SeriesDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
}