package com.example.wowagoodsproject.db.changed

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChangedGoodsEntity::class], version = 4)
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

        /** 상태가 유지된 정보 변경 기록과, 바뀐 뒤 굿즈의 번호 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN statusKept INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN newUid INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 변경된 굿즈 화면에서 고친 기록의 확인 표시 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_changed_goods ADD COLUMN isChecked INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
