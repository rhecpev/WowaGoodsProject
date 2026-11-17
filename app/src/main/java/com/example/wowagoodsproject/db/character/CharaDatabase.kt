package com.example.wowagoodsproject.db.character

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CharaEntity::class], version = 3)
abstract class CharaDatabase : RoomDatabase(){
    abstract fun charaDao(): CharaDao

    companion object {
        /** 선호 캐릭터 우선순위 칸 추가. 기존 선호 캐릭터는 지금 보이던 순서(charaId)대로 1, 2, 3… 을 매긴다. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_character ADD COLUMN charaFavoriteOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE tb_character SET charaFavoriteOrder = (
                        SELECT COUNT(*) FROM tb_character AS other
                        WHERE other.charaIsFavorite = 1 AND other.charaId <= tb_character.charaId
                    ) WHERE charaIsFavorite = 1
                    """.trimIndent()
                )
            }
        }
    }
}
