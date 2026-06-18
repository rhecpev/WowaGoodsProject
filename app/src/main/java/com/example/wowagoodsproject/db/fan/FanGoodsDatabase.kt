package com.example.wowagoodsproject.db.fan

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FanGoodsEntity::class], version = 3)
abstract class FanGoodsDatabase : RoomDatabase() {
    abstract fun fanGoodsDao(): FanGoodsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsMemo TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}