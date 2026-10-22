package com.example.wowagoodsproject

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.wowagoodsproject.db.changed.ChangedGoodsDatabase
import com.example.wowagoodsproject.db.character.CharaDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsDatabase
import com.example.wowagoodsproject.db.news.NewsDatabase
import com.example.wowagoodsproject.db.official.GoodsDatabase
import com.example.wowagoodsproject.db.patchnote.PatchNoteDatabase
import com.example.wowagoodsproject.db.series.SeriesDatabase

/**
 * Robolectric 테스트에서 앱의 DB 7개를 메모리 DB 로 바꿔 끼운다.
 * 앱 코드는 App.database 처럼 전역으로 DB 를 쓰므로, 테스트마다 새로 만들어 서로 섞이지 않게 한다.
 */
object TestDb {

    lateinit var context: Context

    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        App.appContext = context
        App.database = build(GoodsDatabase::class.java)
        App.fanDatabase = build(FanGoodsDatabase::class.java)
        App.charaDatabase = build(CharaDatabase::class.java)
        App.seriesDatabase = build(SeriesDatabase::class.java)
        App.patchNoteDatabase = build(PatchNoteDatabase::class.java)
        App.newsDatabase = build(NewsDatabase::class.java)
        App.changedGoodsDatabase = build(ChangedGoodsDatabase::class.java)
        context.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun tearDown() {
        listOf(
            App.database, App.fanDatabase, App.charaDatabase, App.seriesDatabase,
            App.patchNoteDatabase, App.newsDatabase, App.changedGoodsDatabase
        ).forEach { it.close() }
    }

    private fun <T : RoomDatabase> build(cls: Class<T>): T = Room.inMemoryDatabaseBuilder(context, cls).build()
}
