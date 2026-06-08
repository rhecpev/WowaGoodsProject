package com.example.wowagoodsproject.db.fan

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FanGoodsEntity::class], version = 2)
abstract class FanGoodsDatabase : RoomDatabase() {
    abstract fun fanGoodsDao(): FanGoodsDao
}