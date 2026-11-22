package com.example.wowagoodsproject

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.wowagoodsproject.db.betray.BetrayDatabase
import com.example.wowagoodsproject.db.character.CharaDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsDatabase
import com.example.wowagoodsproject.db.official.GoodsDatabase
import com.example.wowagoodsproject.db.series.SeriesDatabase
import com.example.wowagoodsproject.db.changed.ChangedGoodsDatabase
import com.example.wowagoodsproject.db.news.NewsDatabase
import com.example.wowagoodsproject.db.patchnote.PatchNoteDatabase
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * [ImageLoaderFactory]: 앱 전체가 쓰는 이미지 로더(Coil)의 캐시 크기를 정한다.
 * 기본값은 디스크 캐시가 '기기 남은 용량의 2%, 최대 250MB' 라 저장 공간이 넉넉한 기기에서 크게 불어났다.
 * 디스크 캐시는 받은 원본 파일을 그대로 두므로 한도를 정해 두고, 넘치면 오래 안 본 사진부터 지운다(LRU).
 */
class App : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache")) // Coil 기본 위치 그대로(이미 받은 캐시를 이어 쓴다)
                .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                .build()
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20) // 앱이 쓸 수 있는 메모리의 20%
                .build()
        }
        .build()


    companion object {
        /** 이미지 디스크 캐시 한도 100MB */
        private const val IMAGE_DISK_CACHE_BYTES = 100L * 1024 * 1024

        lateinit var database: GoodsDatabase
        lateinit var fanDatabase: FanGoodsDatabase
        lateinit var charaDatabase: CharaDatabase
        lateinit var seriesDatabase: SeriesDatabase

        lateinit var patchNoteDatabase: PatchNoteDatabase
        lateinit var newsDatabase: NewsDatabase
        lateinit var changedGoodsDatabase: ChangedGoodsDatabase
        /** 배신 굿즈(다른 장르) 전용 DB */
        lateinit var betrayDatabase: BetrayDatabase
        lateinit var appContext: Context

        fun getThemeMode(): Int {
            return appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
                .getInt("theme_mode", 0)
        }

        fun setThemeMode(mode: Int) {
            appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
                .edit().putInt("theme_mode", mode).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext



        database = Room.databaseBuilder(
            applicationContext,
            GoodsDatabase::class.java,
            "goods_database"
        ).addMigrations(
            GoodsDatabase.MIGRATION_1_2,
            GoodsDatabase.MIGRATION_2_3,
            GoodsDatabase.MIGRATION_3_4,
            GoodsDatabase.MIGRATION_4_5,
            GoodsDatabase.MIGRATION_5_6,
            GoodsDatabase.MIGRATION_6_7,
            GoodsDatabase.MIGRATION_7_8,
            GoodsDatabase.MIGRATION_8_9
        ).build()

        fanDatabase = Room.databaseBuilder(
            applicationContext,
            FanGoodsDatabase::class.java,
            "fan_goods_database"
        ).addMigrations(
            FanGoodsDatabase.MIGRATION_1_2,
            FanGoodsDatabase.MIGRATION_2_3,
            FanGoodsDatabase.MIGRATION_3_4,
            FanGoodsDatabase.MIGRATION_4_5,
            FanGoodsDatabase.MIGRATION_5_6,
            FanGoodsDatabase.MIGRATION_6_7,
            FanGoodsDatabase.MIGRATION_7_8,
            FanGoodsDatabase.MIGRATION_8_9
        ).build()

        charaDatabase = Room.databaseBuilder(
            applicationContext,
            CharaDatabase::class.java,
            "chara_database"
        ).addMigrations(
            CharaDatabase.MIGRATION_2_3
        ).build()

        seriesDatabase = Room.databaseBuilder(
            applicationContext,
            SeriesDatabase::class.java,
            "series_database"
        ).build()
        patchNoteDatabase = Room.databaseBuilder(
            applicationContext,
            PatchNoteDatabase::class.java,
            "patch_note_database"
        ).build()

        newsDatabase = Room.databaseBuilder(
            applicationContext,
            NewsDatabase::class.java,
            "news_database"
        ).build()

        changedGoodsDatabase = Room.databaseBuilder(
            applicationContext,
            ChangedGoodsDatabase::class.java,
            "changed_goods_database"
        ).addMigrations(
            ChangedGoodsDatabase.MIGRATION_1_2,
            ChangedGoodsDatabase.MIGRATION_2_3,
            ChangedGoodsDatabase.MIGRATION_3_4
        ).build()

        betrayDatabase = Room.databaseBuilder(
            applicationContext,
            BetrayDatabase::class.java,
            "betray_database"
        ).addMigrations(
            BetrayDatabase.MIGRATION_1_2,
            BetrayDatabase.MIGRATION_2_3,
            BetrayDatabase.MIGRATION_3_4,
            BetrayDatabase.MIGRATION_4_5
        ).build()

        DailySchedule.schedule(this)


    }
}