package com.example.wowagoodsproject.db.official

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GoodsEntity::class], version = 3)
abstract class GoodsDatabase : RoomDatabase() {
    abstract fun goodsDao(): GoodsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsMemo TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsStatus TEXT NOT NULL DEFAULT 'NOT_GOTTEN'")
                db.execSQL("UPDATE tb_goods SET goodsStatus = 'GOTTEN' WHERE goodsIsGotten = 1")
            }
        }
    }
}