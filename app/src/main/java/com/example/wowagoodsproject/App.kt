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
        // 한 서버에서 동시에 받는 사진 수. 통신 라이브러리(OkHttp) 기본값은 5개라, 시리즈를 처음 열어 사진 20장이 필요하면
        // 5장씩 차례로 받느라 늦게 뜬다. 굿즈 사진은 모두 같은 서버(GitHub)에서 오므로 넉넉히 늘린다.
        .okHttpClient {
            okhttp3.OkHttpClient.Builder()
                .dispatcher(okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = IMAGE_MAX_REQUESTS_PER_HOST
                })
                .build()
        }
        .build()


    companion object {
        /**
         * 이미지 디스크 캐시 한도 400MB. 굿즈 사진 전체가 365MB 정도라, 한 번 본 사진이 밀려나 다시 받는 일이 거의 없게 한다.
         * (예전 100MB 는 여기저기 보다 보면 본 사진을 다시 받았다.) 마이페이지 '용량 정리'로 비울 수 있다.
         */
        private const val IMAGE_DISK_CACHE_BYTES = 400L * 1024 * 1024
        /** 한 서버에서 동시에 받는 사진 수(OkHttp 기본 5) */
        private const val IMAGE_MAX_REQUESTS_PER_HOST = 16

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

        // 마이페이지 캐릭터 색을 미리 준비한다(사진에서 뽑은 색 후보를 저장해 두고, 없는 것만 뒤에서 뽑는다)
        com.example.wowagoodsproject.component.CharaColorCache.prewarm(this)


    }
}