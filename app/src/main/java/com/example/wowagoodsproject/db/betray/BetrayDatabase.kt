package com.example.wowagoodsproject.db.betray

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 배신 굿즈 전용 DB. 명조 데이터(공식·2차창작·캐릭터 DB)와 섞이지 않게 따로 둔다. */
@Database(
    entities = [BetrayGenreEntity::class, BetrayGoodsEntity::class, BetrayFavoriteEntity::class, BetrayCharaEntity::class, BetraySeriesEntity::class],
    version = 5
)
abstract class BetrayDatabase : RoomDatabase() {
    abstract fun betrayDao(): BetrayDao

    companion object {
        /** 2: 장르 배경 사진·아이콘 칸 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_betray_genre ADD COLUMN genreImgPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tb_betray_genre ADD COLUMN genreIconPath TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 4: 사용자가 등록한 시리즈(이름·배경·아이콘) */
        /** 5: 시리즈 날짜(만들 때 입력). 예전 시리즈는 빈 값이라 굿즈 최근 등록일을 계속 보인다 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tb_betray_series` ADD COLUMN `seriesDate` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tb_betray_series` (`genreId` INTEGER NOT NULL, `seriesNm` TEXT NOT NULL, " +
                        "`seriesImgPath` TEXT NOT NULL, `seriesIconPath` TEXT NOT NULL, PRIMARY KEY(`genreId`, `seriesNm`))"
                )
            }
        }

        /** 3: 사용자가 추가한 캐릭터(이름·사진) */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tb_betray_chara` (`genreId` INTEGER NOT NULL, `charaNm` TEXT NOT NULL, " +
                        "`charaImgPath` TEXT NOT NULL, PRIMARY KEY(`genreId`, `charaNm`))"
                )
            }
        }
    }
}
