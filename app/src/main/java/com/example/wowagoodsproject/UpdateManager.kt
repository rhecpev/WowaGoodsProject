package com.example.wowagoodsproject

import android.content.Context
import android.util.Log
import com.example.wowagoodsproject.component.GoodsStatus
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

    suspend fun checkAppUpdate(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
                val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
                val lastCheckDate = prefs.getString("last_app_update_check_date", "")
                if (lastCheckDate == today) return@withContext null
                prefs.edit().putString("last_app_update_check_date", today).apply()

                val json = fetchJson("https://api.github.com/repos/rhecpev/WowaGoodsProject/releases/latest")
                val jsonObj = org.json.JSONObject(json)
                val latestTag = jsonObj.getString("tag_name")
                val body = jsonObj.optString("body", "")
                prefs.edit().putString("cached_latest_version", latestTag).apply()
                if (latestTag != BuildConfig.VERSION_NAME) {
                    Pair(latestTag, body)
                } else null
            } catch (e: Exception) {
                Log.d("AppUpdate", "에러: ${e.message}")
                null
            }
        }
    }

    suspend fun updateCharacters(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
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
                contentLines.add("[캐릭터 URL 변경] ${remoteChara.charaNm}\n  ${localChara.charaUrl} → ${remoteChara.charaUrl}")            }
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

         Triple(addedCount, updatedCount, deletedCount)
    }

    suspend fun updateSeries(): Triple<Int, Int, Int> = withContext(Dispatchers.IO){
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
                    contentLines.add("[시리즈 URL 변경] ${remote.seriesNm}\n  ${local.seriesUrl} → ${remote.seriesUrl}")
                if (local.seriesCharas != remote.seriesCharas)
                    contentLines.add("[시리즈 캐릭터 변경] ${remote.seriesNm}\n  ${local.seriesCharas} → ${remote.seriesCharas}")
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

         Triple(addedCount, updatedCount, deletedCount)
    }

    suspend fun updateGoods(): Triple<Int, Int, Int> = withContext(Dispatchers.IO)  {
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
                App.database.goodsDao().insert(remote.copy(goodsId = 0, goodsStatus = GoodsStatus.NOT_GOTTEN.name))
                addedCount++
                contentLines.add("[굿즈 추가] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}")
            } else if (local.goodsUrl != remote.goodsUrl) {
                App.database.goodsDao().update(
                    local.copy(goodsUrl = remote.goodsUrl)
                )
                updatedCount++
                contentLines.add("[굿즈 URL 변경] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}\n  ${local.goodsUrl} → ${remote.goodsUrl}")            }
        }

        val remoteKeys = remoteGoods.map { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsPrice}|${it.goodsMemo}" }
        localGoods.forEach { local ->
            if (!remoteKeys.contains("${local.goodsSeries}|${local.goodsChara}|${local.goodsCategory}|${local.goodsPrice}|${local.goodsMemo}")) {
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

         Triple(addedCount, updatedCount, deletedCount)
    }

    private fun fetchJson(urlStr: String): String {
        android.util.Log.d("UpdateManager", "fetchJson: $urlStr")
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        val result = connection.inputStream.bufferedReader().readText()
        android.util.Log.d("UpdateManager", "fetchJson 완료: ${result.length}자")
        return result
    }
}