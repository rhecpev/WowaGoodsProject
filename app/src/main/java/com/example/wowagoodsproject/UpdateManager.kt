package com.example.wowagoodsproject

import android.content.Context
import android.util.Log
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import com.example.wowagoodsproject.db.news.NewsEntity
import com.example.wowagoodsproject.db.news.NewsType
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.patchnote.PatchNoteEntity
import com.example.wowagoodsproject.db.series.SeriesEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    data class UpdateProgress(val label: String = "", val fraction: Float = 0f) {
        val percent: Int get() = (fraction * 100).toInt().coerceIn(0, 100)
    }

    private val _progress = MutableStateFlow(UpdateProgress())
    val progress: StateFlow<UpdateProgress> = _progress

    private val _isRunning = MutableStateFlow(false)
    /** 데이터 동기화가 돌고 있는지. 시작 화면과 수동 업데이트 팝업이 이 값을 보고 로딩바를 띄운다. */
    val isRunning: StateFlow<Boolean> = _isRunning

    private val updateMutex = Mutex()

    /** 파일별 진행률 가중치. goods.json 이 가장 크므로 비중을 높게 준다. */
    private val steps = listOf(
        Triple("characters.json", "캐릭터", 0.15f),
        Triple("series.json", "시리즈", 0.20f),
        Triple("goods.json", "굿즈", 0.65f)
    )

    fun resetProgress() {
        _progress.value = UpdateProgress()
    }

    /**
     * 캐릭터 → 시리즈 → 굿즈 순으로 전체 동기화하고 바뀐 항목 수를 돌려준다.
     * 앱 시작 시 자동 업데이트와 수동 업데이트(UpdateWorker)가 함께 쓰며, 동시에 두 번 돌지 않는다.
     * 성공하면 마이페이지에 보여줄 마지막 업데이트 시각/변경 개수를 저장한다.
     */
    suspend fun runFullUpdate(): Int = updateMutex.withLock {
        _isRunning.value = true
        resetProgress()
        try {
            val results = listOf(updateCharacters(), updateSeries(), updateGoods())
            val total = results.sumOf { it.first + it.second + it.third }
            val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE).edit()
                .putString("last_update_time", timeStr)
                .putInt("last_update_total", total)
                .apply()
            total
        } finally {
            _isRunning.value = false
        }
    }

    /** localFraction 은 해당 파일 내부에서의 진행률(0f~1f). 전체 진행률로 환산해 보고한다. */
    private fun report(fileName: String, stage: String, localFraction: Float) {
        val index = steps.indexOfFirst { it.first == fileName }
        if (index < 0) return
        val base = steps.take(index).fold(0f) { acc, step -> acc + step.third }
        val fraction = (base + steps[index].third * localFraction.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val next = UpdateProgress("${steps[index].second} $stage", fraction)
        if (next.label != _progress.value.label || next.percent != _progress.value.percent) {
            _progress.value = next
        }
    }

    /** 루프를 돌면서 from~to 구간의 진행률을 함께 보고한다. */
    private inline fun <T> List<T>.forEachReporting(
        fileName: String,
        stage: String,
        from: Float,
        to: Float,
        action: (T) -> Unit
    ) {
        if (isEmpty()) {
            report(fileName, stage, to)
            return
        }
        forEachIndexed { i, item ->
            action(item)
            report(fileName, stage, from + (to - from) * (i + 1) / size)
        }
    }

    private fun getFileLastModified(owner: String, repo: String, path: String): String? {
        return try {
            val url = URL("https://api.github.com/repos/$owner/$repo/commits?path=$path&per_page=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "WowaGoods-Android")
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.d("UpdateManager", "$path 수정 날짜 확인 실패: HTTP ${connection.responseCode} (API 호출 제한 가능)")
                return null
            }

            val json = connection.inputStream.bufferedReader().readText()
            val jsonArray = org.json.JSONArray(json)
            if (jsonArray.length() > 0) {
                jsonArray.getJSONObject(0)
                    .getJSONObject("commit")
                    .getJSONObject("committer")
                    .getString("date")
            } else null
        } catch (e: Exception) {
            Log.d("UpdateManager", "$path 수정 날짜 확인 실패: ${e.message}")
            null
        }
    }

    /**
     * 원격 파일이 바뀐 경우에만 sync 를 실행한다.
     * - 수정 날짜를 못 가져오면(API 제한/네트워크 오류) 스킵하지 않고 그냥 동기화한다.
     * - 수정 날짜는 sync 가 성공한 뒤에만 저장한다. (실패 시 다음 실행에서 재시도)
     */
    private suspend fun syncIfChanged(
        fileName: String,
        prefsKey: String,
        sync: suspend () -> Triple<Int, Int, Int>
    ): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        report(fileName, "확인 중", 0f)
        val lastModified = getFileLastModified("rhecpev", "wuwa-goods-data", fileName)

        if (lastModified != null && lastModified == prefs.getString(prefsKey, "")) {
            Log.d("UpdateManager", "$fileName 변경 없음")
            report(fileName, "최신 상태", 1f)
            return@withContext Triple(0, 0, 0)
        }
        if (lastModified == null) {
            Log.d("UpdateManager", "$fileName 수정 날짜 미확인 - 동기화 그대로 진행")
        }

        report(fileName, "내려받는 중", 0.05f)
        val result = sync()

        if (lastModified != null) {
            prefs.edit().putString(prefsKey, lastModified).apply()
        }
        report(fileName, "완료", 1f)
        result
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

    suspend fun updateCharacters(): Triple<Int, Int, Int> = syncIfChanged("characters.json", "characters_last_modified_v2") {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/characters.json")
        report("characters.json", "비교 중", 0.4f)
        val type = object : TypeToken<List<CharaEntity>>() {}.type
        val remoteCharas: List<CharaEntity> = Gson().fromJson(json, type)
        val localCharas = App.charaDatabase.charaDao().getAll()
        val localMap = localCharas.associateBy { it.charaNm }
        val remoteNameSet = remoteCharas.map { it.charaNm }.toSet()

        val update = UpdateData<CharaEntity>()

        remoteCharas.forEachReporting("characters.json", "적용 중", 0.4f, 0.9f) { remoteChara ->
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

        localCharas.forEachReporting("characters.json", "정리 중", 0.9f, 1f) { localChara ->
            if (localChara.charaNm !in remoteNameSet) {
                App.charaDatabase.charaDao().delete(localChara)
                update.deleted.add("[캐릭터 삭제] ${localChara.charaNm}")
            }
        }

        savePatchNote(update.added + update.updated + update.deleted)
        update.toTriple()
    }

    suspend fun updateSeries(): Triple<Int, Int, Int> = syncIfChanged("series.json", "series_last_modified_v2") {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/series.json")
        report("series.json", "비교 중", 0.4f)
        val type = object : TypeToken<List<SeriesEntity>>() {}.type
        val remoteSeries: List<SeriesEntity> = Gson().fromJson(json, type)
        val localSeries = App.seriesDatabase.seriesDao().getAll()
        val localMap = localSeries.associateBy { "${it.seriesNm}|${it.seriesCountry}" }
        val remoteKeySet = remoteSeries.map { "${it.seriesNm}|${it.seriesCountry}" }.toSet()

        val update = UpdateData<SeriesEntity>()

        remoteSeries.forEachReporting("series.json", "적용 중", 0.4f, 0.9f) { remote ->
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

        localSeries.forEachReporting("series.json", "정리 중", 0.9f, 1f) { local ->
            val key = "${local.seriesNm}|${local.seriesCountry}"
            if (key !in remoteKeySet) {
                App.seriesDatabase.seriesDao().delete(local)
                update.deleted.add("[시리즈 삭제] ${local.seriesNm}")
            }
        }

        savePatchNote(update.added + update.updated + update.deleted)
        update.toTriple()
    }

    suspend fun updateGoods(): Triple<Int, Int, Int> = syncIfChanged("goods.json", "goods_last_modified_v2") {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/goods.json")
        report("goods.json", "비교 중", 0.4f)
        val type = object : TypeToken<List<GoodsEntity>>() {}.type
        val remoteGoods: List<GoodsEntity> = Gson().fromJson(json, type)
        val localGoods = App.database.goodsDao().getAll()
        val localMap = localGoods.associateBy { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsPrice}|${it.goodsMemo}" }
        val remoteKeySet = remoteGoods.map { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsPrice}|${it.goodsMemo}" }.toSet()

        // 소식 생성용 자료. 최초 설치(로컬이 비어 있음)에는 소식을 만들지 않는다.
        val isFirstLoad = localGoods.isEmpty()
        val favorites = App.charaDatabase.charaDao().getAll()
            .filter { it.charaIsFavorite }
            .map { it.charaNm }
        val newsTime = System.currentTimeMillis()
        val news = mutableListOf<NewsEntity>()
        // 가격/메모가 바뀌면 키가 달라져 삭제+추가로 처리되므로, 느슨한 키로 대체 항목을 찾는다.
        val remoteByLooseKey = remoteGoods.groupBy { "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}" }

        val update = UpdateData<GoodsEntity>()

        remoteGoods.forEachReporting("goods.json", "적용 중", 0.4f, 0.9f) { remote ->
            val key = "${remote.goodsSeries}|${remote.goodsChara}|${remote.goodsCategory}|${remote.goodsPrice}|${remote.goodsMemo}"
            val local = localMap[key]
            if (local == null) {
                App.database.goodsDao().insert(remote.copy(goodsId = 0, goodsStatus = GoodsStatus.NOT_GOTTEN.name))
                update.added.add("[굿즈 추가] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}")

                if (!isFirstLoad &&
                    remote.goodsCategory != CATEGORY_SET &&
                    matchesFavorite(remote.goodsChara, favorites)
                ) {
                    news.add(newsOf(NewsType.NEW_GOODS, newsTime, remote))
                }
            } else if (local.goodsUrl != remote.goodsUrl) {
                App.database.goodsDao().update(
                    local.copy(goodsUrl = remote.goodsUrl)
                )
                update.updated.add("[굿즈 URL 변경] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}\n  ${local.goodsUrl} → ${remote.goodsUrl}")

                if (local.goodsStatus != GoodsStatus.NOT_GOTTEN.name && local.goodsCategory != CATEGORY_SET) {
                    news.add(
                        newsOf(
                            NewsType.CHANGED_GOODS, newsTime, remote,
                            "${statusLabel(local.goodsStatus)} 굿즈의 이미지가 변경되었습니다"
                        )
                    )
                }
            }
        }

        localGoods.forEachReporting("goods.json", "정리 중", 0.9f, 1f) { local ->
            val key = "${local.goodsSeries}|${local.goodsChara}|${local.goodsCategory}|${local.goodsPrice}|${local.goodsMemo}"
            if (key !in remoteKeySet) {
                if (!isFirstLoad &&
                    local.goodsStatus != GoodsStatus.NOT_GOTTEN.name &&
                    local.goodsCategory != CATEGORY_SET
                ) {
                    news.add(recordChangedGoods(local, localMap.keys, remoteByLooseKey, newsTime))
                }
                App.database.goodsDao().delete(local)
                update.deleted.add("[굿즈 삭제] ${local.goodsSeries} - ${local.goodsChara} - ${local.goodsCategory} - ${local.goodsPrice}")
            }
        }

        saveNews(news)
        savePatchNote(update.added + update.updated + update.deleted)
        update.toTriple()
    }

    private fun statusLabel(status: String) =
        if (status == GoodsStatus.PENDING.name) "구매 예정" else "보유 중"

    /**
     * 보유/구매예정 굿즈가 사라질 때 호출한다.
     * 같은 시리즈·캐릭터·카테고리의 대체 항목을 찾아 무엇이 바뀌었는지 정리한 뒤
     * tb_changed_goods 에 원래 정보를 보관하고, 그 기록에 연결된 소식을 돌려준다.
     */
    private suspend fun recordChangedGoods(
        local: GoodsEntity,
        localKeys: Set<String>,
        remoteByLooseKey: Map<String, List<GoodsEntity>>,
        newsTime: Long
    ): NewsEntity {
        val looseKey = "${local.goodsSeries}|${local.goodsChara}|${local.goodsCategory}"
        val replacement = remoteByLooseKey[looseKey].orEmpty().firstOrNull { candidate ->
            "$looseKey|${candidate.goodsPrice}|${candidate.goodsMemo}" !in localKeys
        }
        val label = statusLabel(local.goodsStatus)

        val details = mutableListOf<String>()
        if (replacement == null) {
            details.add("$label 굿즈가 원본 데이터에서 삭제되었습니다")
        } else {
            if (local.goodsPrice != replacement.goodsPrice)
                details.add("가격: ${local.goodsPrice} → ${replacement.goodsPrice}")
            if (local.goodsMemo != replacement.goodsMemo)
                details.add("메모: ${local.goodsMemo.ifBlank { "없음" }} → ${replacement.goodsMemo.ifBlank { "없음" }}")
            if (local.goodsUrl != replacement.goodsUrl)
                details.add("이미지가 변경되었습니다")
            if (details.isEmpty()) details.add("굿즈 정보가 변경되었습니다")
            details.add("$label 상태가 초기화되었습니다")
        }
        val detail = details.joinToString("\n")

        val changedId = App.changedGoodsDatabase.changedGoodsDao().insert(
            ChangedGoodsEntity(
                changedTime = newsTime,
                oldSeries = local.goodsSeries,
                oldChara = local.goodsChara,
                oldCategory = local.goodsCategory,
                oldPrice = local.goodsPrice,
                oldMemo = local.goodsMemo,
                oldUrl = local.goodsUrl,
                oldStatus = local.goodsStatus,
                newSeries = replacement?.goodsSeries ?: "",
                newChara = replacement?.goodsChara ?: "",
                newCategory = replacement?.goodsCategory ?: "",
                newPrice = replacement?.goodsPrice ?: "",
                newMemo = replacement?.goodsMemo ?: "",
                newUrl = replacement?.goodsUrl ?: "",
                changedDetail = detail
            )
        ).toInt()

        return newsOf(NewsType.CHANGED_GOODS, newsTime, replacement ?: local, detail)
            .copy(newsChangedId = changedId)
    }

    /** 굿즈의 캐릭터 문자열이 선호 캐릭터와 맞는지. 정확 일치와 부분 일치를 모두 본다. */
    private fun matchesFavorite(goodsChara: String, favorites: List<String>): Boolean {
        if (favorites.isEmpty() || goodsChara.isBlank()) return false
        val tokens = goodsChara.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return favorites.any { fav ->
            goodsChara.equals(fav, ignoreCase = true) ||
                    tokens.any { it.equals(fav, ignoreCase = true) } ||
                    goodsChara.contains(fav, ignoreCase = true)
        }
    }

    private fun newsOf(
        type: NewsType,
        time: Long,
        goods: GoodsEntity,
        detail: String = ""
    ) = NewsEntity(
        newsType = type.name,
        newsTime = time,
        newsSeries = goods.goodsSeries,
        newsChara = goods.goodsChara,
        newsCategory = goods.goodsCategory,
        newsPrice = goods.goodsPrice,
        newsImgUrl = goods.goodsUrl,
        newsDetail = detail
    )

    private suspend fun saveNews(news: List<NewsEntity>) {
        if (news.isEmpty()) return
        // 한 번에 너무 많이 쌓이지 않도록 상한을 둔다.
        App.newsDatabase.newsDao().insertAll(news.take(MAX_NEWS_PER_RUN))
    }

    private const val MAX_NEWS_PER_RUN = 200

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