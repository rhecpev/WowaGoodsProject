package com.example.wowagoodsproject.db.official

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GoodsEntity::class], version = 1)
abstract class GoodsDatabase : RoomDatabase() {
    abstract fun goodsDao(): GoodsDao
}