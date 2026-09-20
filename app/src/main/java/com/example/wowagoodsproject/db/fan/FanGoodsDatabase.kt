package com.example.wowagoodsproject.db.fan

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FanGoodsEntity::class], version = 4)
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

        /** 구매예정 굿즈의 구매일/구입처 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsPurchaseDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsPurchaseStore TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}