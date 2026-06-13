package com.example.wowagoodsproject

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.patchnote.PatchNoteEntity
import com.example.wowagoodsproject.db.series.SeriesEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val PREF_NAME = "wowa_prefs"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    suspend fun checkAndUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val now = System.currentTimeMillis()

        if (now - lastUpdate < ONE_DAY_MS) return

        withContext(Dispatchers.IO) {
            try {
                val charaResult = updateCharacters()
                val seriesResult = updateSeries()
                val goodsResult = updateGoods()
                prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()

                val total = charaResult.first + charaResult.second + charaResult.third +
                        seriesResult.first + seriesResult.second + seriesResult.third +
                        goodsResult.first + goodsResult.second + goodsResult.third
                if (total > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "DB 업데이트 완료! ${total}개 항목 변경됨", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    suspend fun checkAppUpdate(): Pair<String, String>? {
        return try {
            val json = fetchJson("https://api.github.com/repos/rhecpev/WowaGoodsProject/releases/latest")
            val jsonObj = org.json.JSONObject(json)
            val latestTag = jsonObj.getString("tag_name")
            val body = jsonObj.optString("body", "")
            if (latestTag != BuildConfig.VERSION_NAME) {
                Pair(latestTag, body)
            } else null
        } catch (e: Exception) {
            null
        }
    }
    suspend fun updateCharacters(): Triple<Int, Int, Int> {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/characters.json")
        val type = object : TypeToken<List<CharaEntity>>() {}.type
        val remoteCharas: List<CharaEntity> = Gson().fromJson(json, type)
        val localCharas = App.charaDatabase.charaDao().getAll()
        val remoteNames = remoteCharas.map { it.charaNm }

        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0
        val contentLines = mutableListOf<String>()

        remoteCharas.forEach { remoteChara ->
            val localChara = localCharas.find { it.charaNm == remoteChara.charaNm }
            if (localChara == null) {
                App.charaDatabase.charaDao().insert(remoteChara.copy(charaId = 0))
                addedCount++
                contentLines.add("[캐릭터 추가] ${remoteChara.charaNm}")
            } else if (localChara.charaUrl != remoteChara.charaUrl) {
                App.charaDatabase.charaDao().update(
                    localChara.copy(charaUrl = remoteChara.charaUrl)
                )
                updatedCount++
                contentLines.add("[캐릭터 URL 변경] ${remoteChara.charaNm}")
            }
        }

        localCharas.forEach { localChara ->
            if (!remoteNames.contains(localChara.charaNm)) {
                App.charaDatabase.charaDao().delete(localChara)
                deletedCount++
                contentLines.add("[캐릭터 삭제] ${localChara.charaNm}")
            }
        }

        if (contentLines.isNotEmpty()) {
            App.patchNoteDatabase.patchNoteDao().insert(
                PatchNoteEntity(
                    patchTime = System.currentTimeMillis(),
                    patchContent = contentLines.joinToString("\n")
                )
            )
        }

        return Triple(addedCount, updatedCount, deletedCount)
    }

    suspend fun updateSeries(): Triple<Int, Int, Int> {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/series.json")
        val type = object : TypeToken<List<SeriesEntity>>() {}.type
        val remoteSeries: List<SeriesEntity> = Gson().fromJson(json, type)
        val localSeries = App.seriesDatabase.seriesDao().getAll()

        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0
        val contentLines = mutableListOf<String>()

        remoteSeries.forEach { remote ->
            val local = localSeries.find {
                it.seriesNm == remote.seriesNm && it.seriesCountry == remote.seriesCountry
            }
            if (local == null) {
                App.seriesDatabase.seriesDao().insert(remote.copy(seriesId = 0))
                addedCount++
                contentLines.add("[시리즈 추가] ${remote.seriesNm}")
            } else if (
                local.seriesUrl != remote.seriesUrl ||
                local.seriesCharas != remote.seriesCharas ||
                local.seriesDate != remote.seriesDate
            ) {
                App.seriesDatabase.seriesDao().update(
                    local.copy(
                        seriesUrl = remote.seriesUrl,
                        seriesCharas = remote.seriesCharas,
                        seriesDate = remote.seriesDate
                    )
                )
                updatedCount++
                if (local.seriesUrl != remote.seriesUrl)
                    contentLines.add("[시리즈 URL 변경] ${remote.seriesNm}")
                if (local.seriesCharas != remote.seriesCharas)
                    contentLines.add("[시리즈 캐릭터 변경] ${remote.seriesNm}")
            }
        }

        val remoteKeys = remoteSeries.map { "${it.seriesNm}|${it.seriesCountry}" }
        localSeries.forEach { local ->
            if (!remoteKeys.contains("${local.seriesNm}|${local.seriesCountry}")) {
                App.seriesDatabase.seriesDao().delete(local)
                deletedCount++
                contentLines.add("[시리즈 삭제] ${local.seriesNm}")
            }
        }

        if (contentLines.isNotEmpty()) {
            App.patchNoteDatabase.patchNoteDao().insert(
                PatchNoteEntity(
                    patchTime = System.currentTimeMillis(),
                    patchContent = contentLines.joinToString("\n")
                )
            )
        }

        return Triple(addedCount, updatedCount, deletedCount)
    }
    suspend fun updateGoods(): Triple<Int, Int, Int> {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/goods.json")
        val type = object : TypeToken<List<GoodsEntity>>() {}.type
        val remoteGoods: List<GoodsEntity> = Gson().fromJson(json, type)
        val localGoods = App.database.goodsDao().getAll()

        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0
        val contentLines = mutableListOf<String>()

        remoteGoods.forEach { remote ->
            val local = localGoods.find {
                it.goodsSeries == remote.goodsSeries &&
                        it.goodsChara == remote.goodsChara &&
                        it.goodsCategory == remote.goodsCategory &&
                        it.goodsPrice == remote.goodsPrice &&
                        it.goodsMemo == remote.goodsMemo
            }
            if (local == null) {
                App.database.goodsDao().insert(remote.copy(goodsId = 0, goodsIsGotten = false))
                addedCount++
                contentLines.add("[굿즈 추가] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}")
            } else if (local.goodsUrl != remote.goodsUrl) {
                App.database.goodsDao().update(
                    local.copy(goodsUrl = remote.goodsUrl)
                )
                updatedCount++
                contentLines.add("[굿즈 URL 변경] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}")
            }
        }

        val remoteKeys = remoteGoods.map { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsPrice}|${it.goodsMemo}" }
        localGoods.forEach { local ->
            if (!remoteKeys.contains("${local.goodsSeries}|${local.goodsChara}|${local.goodsCategory}|${local.goodsPrice}|${local.goodsMemo}")) {
                val key = "${local.goodsSeries}|${local.goodsChara}|${local.goodsCategory}|${local.goodsPrice}|${local.goodsMemo}"
                Log.d("DeleteGoods", "삭제: $key")
                App.database.goodsDao().delete(local)
                deletedCount++
                contentLines.add("[굿즈 삭제] ${local.goodsSeries} - ${local.goodsChara} - ${local.goodsCategory} - ${local.goodsPrice}")
            }
        }

        if (contentLines.isNotEmpty()) {
            App.patchNoteDatabase.patchNoteDao().insert(
                PatchNoteEntity(
                    patchTime = System.currentTimeMillis(),
                    patchContent = contentLines.joinToString("\n")
                )
            )
        }

        return Triple(addedCount, updatedCount, deletedCount)
    }

    private fun fetchJson(urlStr: String): String {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        return connection.inputStream.bufferedReader().readText()
    }
}