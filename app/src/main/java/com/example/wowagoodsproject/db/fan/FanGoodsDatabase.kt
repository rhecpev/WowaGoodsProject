package com.example.wowagoodsproject.db.fan

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wowagoodsproject.db.convertIntegerColumnToText

@Database(entities = [FanGoodsEntity::class, FanSeriesEntity::class], version = 9)
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
                db.convertIntegerColumnToText("tb_fan_goods", "fanGoodsPrice")
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

        /** 구매예정 굿즈의 수령예정일 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsReceiveDate TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 보유/구매예정 수량 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsQuantity INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** 시리즈 날짜(만들 때 입력). 예전 시리즈는 빈 값이라 굿즈 최근 등록일을 계속 보인다 */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tb_fan_series` ADD COLUMN `seriesDate` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 사용자가 등록한 2차창작 시리즈(이름·배경·아이콘) */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tb_fan_series` (`seriesNm` TEXT NOT NULL, `seriesImgPath` TEXT NOT NULL, " +
                        "`seriesIconPath` TEXT NOT NULL, PRIMARY KEY(`seriesNm`))"
                )
            }
        }

        /** 구매예정 굿즈의 배송 시작일 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_fan_goods ADD COLUMN fanGoodsShippingDate TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}