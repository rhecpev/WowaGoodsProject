package com.example.wowagoodsproject.db.official

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GoodsEntity::class], version = 7)
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

        /** 구매예정 굿즈의 구매일/구입처 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsPurchaseDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsPurchaseStore TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 구매예정 굿즈의 수령예정일 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsReceiveDate TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 보유/구매예정 수량 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsQuantity INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** 구매예정 굿즈의 배송 시작일 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsShippingDate TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}