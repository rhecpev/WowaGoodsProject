package com.example.wowagoodsproject

import android.app.Application
import android.content.Context
import androidx.room.Room
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

class App : Application() {

    companion object {
        lateinit var database: GoodsDatabase
        lateinit var fanDatabase: FanGoodsDatabase
        lateinit var charaDatabase: CharaDatabase
        lateinit var seriesDatabase: SeriesDatabase

        lateinit var patchNoteDatabase: PatchNoteDatabase
        lateinit var newsDatabase: NewsDatabase
        lateinit var changedGoodsDatabase: ChangedGoodsDatabase
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
            GoodsDatabase.MIGRATION_3_4
        ).build()

        fanDatabase = Room.databaseBuilder(
            applicationContext,
            FanGoodsDatabase::class.java,
            "fan_goods_database"
        ).addMigrations(
            FanGoodsDatabase.MIGRATION_1_2,
            FanGoodsDatabase.MIGRATION_2_3,
            FanGoodsDatabase.MIGRATION_3_4
        ).build()

        charaDatabase = Room.databaseBuilder(
            applicationContext,
            CharaDatabase::class.java,
            "chara_database"
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
        ).build()


    }
}