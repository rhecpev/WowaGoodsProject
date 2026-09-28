package com.example.wowagoodsproject.db.changed

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChangedGoodsEntity::class], version = 2)
abstract class ChangedGoodsDatabase : RoomDatabase() {
    abstract fun changedGoodsDao(): ChangedGoodsDao

    companion object {
        /** 되돌릴 때 필요한 수량/구매 정보/배송 시작일 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN oldQuantity INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN oldPurchaseStore TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN oldPurchaseDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN oldReceiveDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN oldShippingDate TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
