package com.example.wowagoodsproject.db.official

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wowagoodsproject.db.convertIntegerColumnToText

@Database(entities = [GoodsEntity::class], version = 8)
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
                db.convertIntegerColumnToText("tb_goods", "goodsPrice")
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

        /** 원본 데이터의 굿즈 번호. 기존 굿즈는 다음 데이터 업데이트 때 내용으로 짝을 찾아 채운다. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_goods ADD COLUMN goodsUid INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}