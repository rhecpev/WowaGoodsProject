package com.example.wowagoodsproject

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.wowagoodsproject.db.character.CharaDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsDatabase
import com.example.wowagoodsproject.db.official.GoodsDatabase
import com.example.wowagoodsproject.db.series.SeriesDatabase
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
    private fun scheduleUpdateWorker() {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = midnight.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_update",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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
        patchNoteDatabase = Room.databaseBuilder(
            applicationContext,
            PatchNoteDatabase::class.java,
            "patch_note_database"
        ).build()

        // onCreate() 안에 추가
        scheduleUpdateWorker()
        val prefs = getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        if (isFirstRun) {
            prefs.edit().putBoolean("is_first_run", false).apply()
            WorkManager.getInstance(this).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>().build()
            )
        }
    }
}