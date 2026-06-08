package com.example.wowagoodsproject

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.wowagoodsproject.db.character.CharaDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsDatabase
import com.example.wowagoodsproject.db.official.GoodsDatabase
import com.example.wowagoodsproject.db.series.SeriesDatabase

class App : Application() {

    companion object {
        lateinit var database: GoodsDatabase
        lateinit var fanDatabase: FanGoodsDatabase
        lateinit var charaDatabase: CharaDatabase
        lateinit var seriesDatabase: SeriesDatabase
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
        ).addMigrations(GoodsDatabase.MIGRATION_1_2).build()

        fanDatabase = Room.databaseBuilder(
            applicationContext,
            FanGoodsDatabase::class.java,
            "fan_goods_database"
        ).addMigrations(FanGoodsDatabase.MIGRATION_1_2).build()

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
    }
}