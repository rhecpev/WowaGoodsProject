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
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsMemo TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsStatus TEXT NOT NULL DEFAULT 'NOT_GOTTEN'")
                db.execSQL("UPDATE tb_fan_goods SET fanGoodsStatus = 'GOTTEN' WHERE fanGoodsIsGotten = 1")
            }
        }
    }
}