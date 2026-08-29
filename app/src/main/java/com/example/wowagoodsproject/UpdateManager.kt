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

    private suspend fun getFileLastModified(owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/commits?path=$path&per_page=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val json = connection.inputStream.bufferedReader().readText()
            val jsonArray = org.json.JSONArray(json)
            return@withContext if (jsonArray.length() > 0) {
                jsonArray.getJSONObject(0)
                    .getJSONObject("commit")
                    .getJSONObject("committer")
                    .getString("date")
            } else null
        } catch (e: Exception) {
            Log.d("UpdateManager", "수정 날짜 확인 실패: ${e.message}")
            null
        }
    }

    private suspend fun checkAndSaveModifiedDate(prefs: android.content.SharedPreferences, key: String, lastModified: String?): Boolean {
        if (lastModified == null) return false
        val savedModified = prefs.getString(key, "")
        if (lastModified == savedModified) return false
        prefs.edit().putString(key, lastModified).apply()
        return true
    }

    private data class UpdateData<T>(
        val added: MutableList<String> = mutableListOf(),
        val updated: MutableList<String> = mutableListOf(),
        val deleted: MutableList<String> = mutableListOf()
    ) {
        fun toTriple() = Triple(added.size, updated.size, deleted.size)
    }

    suspend fun checkAppUpdate(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
                val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
                val lastCheckDate = prefs.getString("last_app_update_check_date", "")
                if (lastCheckDate == today) return@withContext null

                val json = fetchJson("https://api.github.com/repos/rhecpev/WowaGoodsProject/releases/latest")
                val jsonObj = org.json.JSONObject(json)
                val latestTag = jsonObj.getString("tag_name")
                val body = jsonObj.optString("body", "")

                prefs.edit()
                    .putString("last_app_update_check_date", today)
                    .putString("cached_latest_version", latestTag)
                    .apply()

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
        val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        val lastModified = getFileLastModified("rhecpev", "wuwa-goods-data", "characters.json")

        if (!checkAndSaveModifiedDate(prefs, "characters_last_modified", lastModified)) {
            Log.d("UpdateManager", "characters.json 변경 없음")
            return@withContext Triple(0, 0, 0)
        }

        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/characters.json")
        val type = object : TypeToken<List<CharaEntity>>() {}.type
        val remoteCharas: List<CharaEntity> = Gson().fromJson(json, type)
        val localCharas = App.charaDatabase.charaDao().getAll()
        val localMap = localCharas.associateBy { it.charaNm }
        val remoteNameSet = remoteCharas.map { it.charaNm }.toSet()

        val update = UpdateData<CharaEntity>()

        remoteCharas.forEach { remoteChara ->
            val localChara = localMap[remoteChara.charaNm]
            if (localChara == null) {
                App.charaDatabase.charaDao().insert(remoteChara.copy(charaId = 0))
                update.added.add("[캐릭터 추가] ${remoteChara.charaNm}")
            } else if (localChara.charaUrl != remoteChara.charaUrl) {
                App.charaDatabase.charaDao().update(
                    localChara.copy(charaUrl = remoteChara.charaUrl)
                )
                update.updated.add("[캐릭터 URL 변경] ${remoteChara.charaNm}\n  ${localChara.charaUrl} → ${remoteChara.charaUrl}")
            }
        }

        localCharas.forEach { localChara ->
            if (localChara.charaNm !in remoteNameSet) {
                App.charaDatabase.charaDao().delete(localChara)
                update.deleted.add("[캐릭터 삭제] ${localChara.charaNm}")
            }
        }

        savePatchNote(update.added + update.updated + update.deleted)
        return@withContext update.toTriple()
    }

    suspend fun updateSeries(): Triple<Int, Int, Int> = withContext(Dispatchers.IO){
        val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        val lastModified = getFileLastModified("rhecpev", "wuwa-goods-data", "series.json")

        if (!checkAndSaveModifiedDate(prefs, "series_last_modified", lastModified)) {
            Log.d("UpdateManager", "series.json 변경 없음")
            return@withContext Triple(0, 0, 0)
        }

        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/series.json")
        val type = object : TypeToken<List<SeriesEntity>>() {}.type
        val remoteSeries: List<SeriesEntity> = Gson().fromJson(json, type)
        val localSeries = App.seriesDatabase.seriesDao().getAll()
        val localMap = localSeries.associateBy { "${it.seriesNm}|${it.seriesCountry}" }
        val remoteKeySet = remoteSeries.map { "${it.seriesNm}|${it.seriesCountry}" }.toSet()

        val update = UpdateData<SeriesEntity>()

        remoteSeries.forEach { remote ->
            val key = "${remote.seriesNm}|${remote.seriesCountry}"
            val local = localMap[key]
            if (local == null) {
                App.seriesDatabase.seriesDao().insert(remote.copy(seriesId = 0))
                update.added.add("[시리즈 추가] ${remote.seriesNm}")
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
                if (local.seriesUrl != remote.seriesUrl)
                    update.updated.add("[시리즈 URL 변경] ${remote.seriesNm}\n  ${local.seriesUrl} → ${remote.seriesUrl}")
                if (local.seriesCharas != remote.seriesCharas)
                    update.updated.add("[시리즈 캐릭터 변경] ${remote.seriesNm}\n  ${local.seriesCharas} → ${remote.seriesCharas}")
            }
        }

        localSeries.forEach { local ->
            val key = "${local.seriesNm}|${local.seriesCountry}"
            if (key !in remoteKeySet) {
                App.seriesDatabase.seriesDao().delete(local)
                update.deleted.add("[시리즈 삭제] ${local.seriesNm}")
            }
        }

        savePatchNote(update.added + update.updated + update.deleted)
        return@withContext update.toTriple()
    }

    suspend fun updateGoods(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        val lastModified = getFileLastModified("rhecpev", "wuwa-goods-data", "goods.json")

        if (!checkAndSaveModifiedDate(prefs, "goods_last_modified", lastModified)) {
            Log.d("UpdateManager", "goods.json 변경 없음")
            return@withContext Triple(0, 0, 0)
        }

        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/goods.json")
        val type = object : TypeToken<List<GoodsEntity>>() {}.type
        val remoteGoods: List<GoodsEntity> = Gson().fromJson(json, type)
        val localGoods = App.database.goodsDao().getAll()
        val localMap = localGoods.associateBy { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsPrice}|${it.goodsMemo}" }
        val remoteKeySet = remoteGoods.map { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsPrice}|${it.goodsMemo}" }.toSet()

        val update = UpdateData<GoodsEntity>()

        remoteGoods.forEach { remote ->
            val key = "${remote.goodsSeries}|${remote.goodsChara}|${remote.goodsCategory}|${remote.goodsPrice}|${remote.goodsMemo}"
            val local = localMap[key]
            if (local == null) {
                App.database.goodsDao().insert(remote.copy(goodsId = 0, goodsStatus = GoodsStatus.NOT_GOTTEN.name))
                update.added.add("[굿즈 추가] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}")
            } else if (local.goodsUrl != remote.goodsUrl) {
                App.database.goodsDao().update(
                    local.copy(goodsUrl = remote.goodsUrl)
                )
                update.updated.add("[굿즈 URL 변경] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}\n  ${local.goodsUrl} → ${remote.goodsUrl}")
            }
        }

        localGoods.forEach { local ->
            val key = "${local.goodsSeries}|${local.goodsChara}|${local.goodsCategory}|${local.goodsPrice}|${local.goodsMemo}"
            if (key !in remoteKeySet) {
                App.database.goodsDao().delete(local)
                update.deleted.add("[굿즈 삭제] ${local.goodsSeries} - ${local.goodsChara} - ${local.goodsCategory} - ${local.goodsPrice}")
            }
        }

        savePatchNote(update.added + update.updated + update.deleted)
        return@withContext update.toTriple()
    }

    private suspend fun savePatchNote(contentLines: List<String>) {
        if (contentLines.isNotEmpty()) {
            App.patchNoteDatabase.patchNoteDao().insert(
                PatchNoteEntity(
                    patchTime = System.currentTimeMillis(),
                    patchContent = contentLines.joinToString("\n")
                )
            )
        }
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