package com.example.wowagoodsproject.db.changed

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChangedGoodsEntity::class], version = 1)
abstract class ChangedGoodsDatabase : RoomDatabase() {
    abstract fun changedGoodsDao(): ChangedGoodsDao
}
