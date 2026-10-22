package com.example.wowagoodsproject

import com.example.wowagoodsproject.db.official.modify
import androidx.room.withTransaction
import android.content.Context
import android.util.Log
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.hasChara
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
    /** 데이터 동기화가 돌고 있는지(오전 5시 백그라운드 업데이트 포함). */
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _showProgress = MutableStateFlow(false)
    /** 화면을 막고 진행률을 보여 줄 동기화(첫 설치, 수동 업데이트)가 돌고 있는지. 오전 5시 업데이트는 조용히 돈다. */
    val showProgress: StateFlow<Boolean> = _showProgress

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
     * 오전 5시 업데이트(ScheduledUpdateWorker), 수동 업데이트(UpdateWorker), 첫 설치 때 앱 시작이 함께 쓰며,
     * 동시에 두 번 돌지 않는다. 성공하면 마이페이지에 보여줄 마지막 업데이트 시각/변경 개수를 저장한다.
     * 새 소식이 쌓이면 소식 알림을 띄운다.
     *
     * @param showProgress false 면 진행률 팝업 없이 조용히 돈다(오전 5시 업데이트).
     */
    suspend fun runFullUpdate(showProgress: Boolean = true): Int = updateMutex.withLock {
        _isRunning.value = true
        _showProgress.value = showProgress
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
            _showProgress.value = false
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

                if (isNewerVersion(latestTag, BuildConfig.VERSION_NAME)) {
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
        val remoteCharas: List<CharaEntity> = parseRemoteList(json, "characters.json")
        val update = UpdateData<CharaEntity>()
        // 한 트랜잭션으로 묶는다. 읽기부터 안에서 해야, 그사이 사용자가 바꾼 선호 표시를 옛 값으로 덮어쓰지 않는다.
        App.charaDatabase.withTransaction {
            val localCharas = App.charaDatabase.charaDao().getAll()
            val localMap = localCharas.associateBy { it.charaNm }
            val remoteNameSet = remoteCharas.map { it.charaNm }.toSet()

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
        }

        savePatchNote(update.added + update.updated + update.deleted)
        update.toTriple()
    }

    suspend fun updateSeries(): Triple<Int, Int, Int> = syncIfChanged("series.json", "series_last_modified_v2") {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/series.json")
        report("series.json", "비교 중", 0.4f)
        val remoteSeries: List<SeriesEntity> = parseRemoteList(json, "series.json")
        val update = UpdateData<SeriesEntity>()
        // 한 트랜잭션으로 묶어 빠르게 쓰고, 도중에 앱이 꺼져도 반만 바뀐 채로 남지 않게 한다.
        App.seriesDatabase.withTransaction {
            val localSeries = App.seriesDatabase.seriesDao().getAll()
            val localMap = localSeries.associateBy { "${it.seriesNm}|${it.seriesCountry}" }
            val remoteKeySet = remoteSeries.map { "${it.seriesNm}|${it.seriesCountry}" }.toSet()

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
        }

        savePatchNote(update.added + update.updated + update.deleted)
        update.toTriple()
    }

    // v3: 굿즈 번호(goodsUid)를 채우려고, 원본이 그대로여도 이 버전으로 올린 뒤 한 번은 받는다.
    suspend fun updateGoods(): Triple<Int, Int, Int> = syncIfChanged("goods.json", "goods_last_modified_v3") {
        val json = fetchJson("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/goods.json")
        report("goods.json", "비교 중", 0.4f)
        applyGoodsSync(parseRemoteList(json, "goods.json"))
    }

    /** 내려받은 원본 굿즈 목록을 폰에 반영하고 (추가, 변경, 삭제) 수를 돌려준다. 내려받기와 떼어 놓아 테스트가 바로 부른다. */
    internal suspend fun applyGoodsSync(remoteGoods: List<GoodsEntity>): Triple<Int, Int, Int> {
        requireGoodsUids(remoteGoods)
        val localGoods = App.database.goodsDao().getAll()
        val plan = planGoodsSync(localGoods, remoteGoods)

        // 소식 생성용 자료. 최초 설치(로컬이 비어 있음)에는 소식을 만들지 않는다.
        val isFirstLoad = localGoods.isEmpty()
        val favorites = App.charaDatabase.charaDao().getAll()
            .filter { it.charaIsFavorite }
            .map { it.charaNm }
        val newsTime = System.currentTimeMillis()
        val news = mutableListOf<NewsEntity>()
        val localKeys = localGoods.map { it.contentKey() }.toSet()
        // 번호가 바뀐 굿즈는 삭제+추가로 처리되므로, 느슨한 키로 대체 항목을 찾는다.
        val remoteByLooseKey = remoteGoods.groupBy { it.looseKey() }
        // 이번에 사라지는 로컬 굿즈의 느슨한 키. 같은 키로 새로 들어온 굿즈는 신규가 아니라 정보가 바뀐 것이다.
        val replacedLooseKeys = plan.deletes.map { it.looseKey() }.toSet()

        val update = UpdateData<GoodsEntity>()
        // 변경 기록과 그 소식. 기록은 다른 DB 라 굿즈 트랜잭션이 실패해도 되돌려지지 않으므로,
        // 여기 모아 두었다가 굿즈 저장이 끝난 뒤에 넣는다. 그래야 재시도 때 같은 기록이 겹쳐 쌓이지 않는다.
        val changedRecords = mutableListOf<Pair<ChangedGoodsEntity, NewsEntity>>()

        // 한 트랜잭션으로 묶어 빠르게 쓰고, 도중에 앱이 꺼져도 반만 바뀐 채로 남지 않게 한다.
        App.database.withTransaction {
            plan.updates.forEachReporting("goods.json", "적용 중", 0.4f, 0.6f) { (local, remote) ->
                // 업데이트는 새벽에 조용히 돌기도 해서 그사이 사용자가 바꾼 값이 있을 수 있다.
                // 시작할 때 읽어 둔 값을 통째로 저장하지 않고 원본 칸만 고친다.
                App.database.goodsDao().modify(local.goodsId) { it.withSourceOf(remote) }

                val details = sourceChanges(local, remote)
                // 번호만 처음 받은 굿즈는 바뀐 것이 없으니 기록하지 않는다.
                if (details.isEmpty()) return@forEachReporting
                update.updated.add("[굿즈 정보 변경] ${local.goodsSeries} - ${local.goodsChara} - ${local.goodsCategory} - ${local.goodsPrice}\n  ${details.joinToString("\n  ")}")

                if (local.goodsStatus != GoodsStatus.NOT_GOTTEN.name && remote.goodsCategory != CATEGORY_SET) {
                    val label = statusLabel(local.goodsStatus)
                    val detail = if (details == listOf(IMAGE_CHANGED)) "$label 굿즈의 이미지가 변경되었습니다"
                    else (details + "$label 상태는 그대로 유지됩니다").joinToString("\n")
                    // 마이페이지의 변경된 굿즈에 남긴다. 상태는 그대로라 되돌릴 것은 없다.
                    changedRecords.add(
                        changedRecord(local, remote, newsTime, detail).copy(statusKept = true) to
                            newsOf(NewsType.CHANGED_GOODS, newsTime, remote, detail)
                    )
                }
            }

            plan.inserts.forEachReporting("goods.json", "추가 중", 0.6f, 0.9f) { remote ->
                App.database.goodsDao().insert(remote.copy(goodsId = 0, goodsStatus = GoodsStatus.NOT_GOTTEN.name))
                update.added.add("[굿즈 추가] ${remote.goodsSeries} - ${remote.goodsChara} - ${remote.goodsCategory} - ${remote.goodsPrice}")

                if (!isFirstLoad &&
                    remote.goodsCategory != CATEGORY_SET &&
                    remote.looseKey() !in replacedLooseKeys &&
                    matchesFavorite(remote.goodsChara, favorites)
                ) {
                    news.add(newsOf(NewsType.NEW_GOODS, newsTime, remote))
                }
            }

            plan.deletes.forEachReporting("goods.json", "정리 중", 0.9f, 1f) { local ->
                if (!isFirstLoad &&
                    local.goodsStatus != GoodsStatus.NOT_GOTTEN.name &&
                    local.goodsCategory != CATEGORY_SET
                ) {
                    // 사라지기 직전의 최신 값(수량·구매 정보)을 기록해 둔다.
                    val latest = App.database.goodsDao().getById(local.goodsId) ?: local
                    changedRecords.add(removedGoodsRecord(latest, localKeys, remoteByLooseKey, newsTime))
                }
                App.database.goodsDao().delete(local)
                update.deleted.add("[굿즈 삭제] ${local.goodsSeries} - ${local.goodsChara} - ${local.goodsCategory} - ${local.goodsPrice}")
            }
        }

        changedRecords.forEach { (record, newsItem) ->
            val changedId = App.changedGoodsDatabase.changedGoodsDao().insert(record).toInt()
            news.add(newsItem.copy(newsChangedId = changedId))
        }
        saveNews(news)
        savePatchNote(update.added + update.updated + update.deleted)
        return update.toTriple()
    }

    private const val IMAGE_CHANGED = "이미지가 변경되었습니다"

    /** 같은 번호의 굿즈에서 원본 칸이 무엇이 바뀌었는지. 번호만 채워진 경우는 빈 목록이다. */
    private fun sourceChanges(local: GoodsEntity, remote: GoodsEntity): List<String> {
        val changes = mutableListOf<String>()
        fun compare(label: String, old: String, new: String) {
            if (old != new) changes.add("$label: ${old.ifBlank { "없음" }} → ${new.ifBlank { "없음" }}")
        }
        compare("시리즈", local.goodsSeries, remote.goodsSeries)
        compare("캐릭터", local.goodsChara, remote.goodsChara)
        compare("카테고리", local.goodsCategory, remote.goodsCategory)
        compare("가격", local.goodsPrice, remote.goodsPrice)
        compare("메모", local.goodsMemo, remote.goodsMemo)
        compare("발매일", local.goodsReleaseDate, remote.goodsReleaseDate)
        if (local.goodsUrl != remote.goodsUrl) changes.add(IMAGE_CHANGED)
        return changes
    }

    private fun statusLabel(status: String) =
        if (status == GoodsStatus.PENDING.name) "구매 예정" else "보유 중"

    /**
     * 보유/구매예정 굿즈가 사라질 때 호출한다.
     * 같은 시리즈·캐릭터·카테고리의 대체 항목을 찾아 무엇이 바뀌었는지 정리한 뒤
     * tb_changed_goods 에 넣을 기록과 그 소식을 돌려준다. 넣는 것은 굿즈 저장이 끝난 뒤 부르는 쪽이 한다.
     */
    private fun removedGoodsRecord(
        local: GoodsEntity,
        localKeys: Set<String>,
        remoteByLooseKey: Map<String, List<GoodsEntity>>,
        newsTime: Long
    ): Pair<ChangedGoodsEntity, NewsEntity> {
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

        return changedRecord(local, replacement, newsTime, detail) to
            newsOf(NewsType.CHANGED_GOODS, newsTime, replacement ?: local, detail)
    }

    /** 바뀌기 전 굿즈([local], 사용자 기록 포함)와 바뀐 뒤 굿즈([new], 완전 삭제면 null)의 기록 */
    private fun changedRecord(local: GoodsEntity, new: GoodsEntity?, time: Long, detail: String) = ChangedGoodsEntity(
        changedTime = time,
        oldSeries = local.goodsSeries,
        oldChara = local.goodsChara,
        oldCategory = local.goodsCategory,
        oldPrice = local.goodsPrice,
        oldMemo = local.goodsMemo,
        oldUrl = local.goodsUrl,
        oldStatus = local.goodsStatus,
        newSeries = new?.goodsSeries ?: "",
        newChara = new?.goodsChara ?: "",
        newCategory = new?.goodsCategory ?: "",
        newPrice = new?.goodsPrice ?: "",
        newMemo = new?.goodsMemo ?: "",
        newUrl = new?.goodsUrl ?: "",
        newUid = new?.goodsUid ?: 0,
        changedDetail = detail,
        oldQuantity = local.goodsQuantity,
        oldPurchaseStore = local.goodsPurchaseStore,
        oldPurchaseDate = local.goodsPurchaseDate,
        oldReceiveDate = local.goodsReceiveDate,
        oldShippingDate = local.goodsShippingDate
    )

    /** 굿즈의 캐릭터 문자열에 선호 캐릭터가 한 명으로 들어 있는지. 글자 일부만 같은 다른 캐릭터는 아니다. */
    private fun matchesFavorite(goodsChara: String, favorites: List<String>): Boolean =
        favorites.any { hasChara(goodsChara, it) }

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
        val saved = news.take(MAX_NEWS_PER_RUN)
        App.newsDatabase.newsDao().insertAll(saved)
        // 소식에 무언가 새로 쌓이면 알림으로도 알린다.
        NewsNotifier.notifyNews(App.appContext, saved)
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

    /**
     * 원격 목록을 읽는다. 비어 있으면 로컬 항목(사용자의 보유/구매예정 상태 포함)이 모두 지워지므로
     * 원본 데이터가 잘못 올라온 것으로 보고 동기화를 멈춘다. 수정 날짜를 저장하지 않으니 다음 실행에서 다시 시도한다.
     */
    private inline fun <reified T> parseRemoteList(json: String, fileName: String): List<T> {
        val type = object : TypeToken<List<T>>() {}.type
        val list: List<T>? = Gson().fromJson(json, type)
        if (list.isNullOrEmpty()) throw IllegalStateException("$fileName 이 비어 있어 동기화를 건너뜁니다")
        return list
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