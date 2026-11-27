package com.example.wowagoodsproject.screen.mypage

import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.goodsStatusOf
import com.example.wowagoodsproject.db.betray.charaNames
import com.example.wowagoodsproject.screen.betray.asCharaEntity
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.ui.theme.AppStyles
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/*
 * 백업 불러오기 전 확인. 파일을 열어 큰 항목별로 지금 기기 데이터와 무엇이 다른지 정리하고,
 * 사람이 항목을 골라 불러오게 한다(BackupZip.restore 의 RestoreParts).
 * 배신 굿즈가 들어 있으면 함께 불러올지 따로 묻는다(처음에는 꺼 둔다).
 */

/** 불러오기를 고를 수 있는 큰 항목 */
enum class ImportPart(val title: String) {
    OFFICIAL("공식 굿즈 보유 현황"),
    FAN("비공식 굿즈"),
    FAN_SERIES("비공식 시리즈"),
    FAVORITES("선호 캐릭터"),
    BETRAY("배신 굿즈")
}

/** 한 항목의 지금 → 파일 비교 */
data class ImportSection(
    val part: ImportPart,
    val now: String,
    val file: String,
    val changed: Boolean,
    val details: List<String> = emptyList(),
    /** Before / After 표: 공식은 보유, 2차창작은 굿즈 수 */
    val rows: List<DiffRow> = emptyList(),
    /** 공식 구매예정 Before / After */
    val pendingRows: List<DiffRow> = emptyList(),
    val favRows: List<FavRow> = emptyList(),
    /** 배신 굿즈 장르별 Before / After */
    val genres: List<GenreDiff> = emptyList()
)

/** 보고서의 캐릭터 한 줄: 파일 안 공식(보유·구매예정)·2차창작 굿즈 수 */
data class ReportChara(val name: String, val avatar: String, val official: Int, val fan: Int) {
    val total get() = official + fan
}

/** 파일에 든 것을 한눈에 보는 보고서(불러오기 전 확인 맨 위) */
data class ImportReport(
    val officialGotten: Int = 0,
    val officialPending: Int = 0,
    val fanTotal: Int = 0,
    val fanSeries: Int = 0,
    val photos: Int = 0,
    /** 선호 우선순위 순 */
    val favorites: List<ReportChara> = emptyList(),
    /** 굿즈가 많은 순 */
    val charas: List<ReportChara> = emptyList(),
    /** (장르 이름, 굿즈 수) */
    val betrayGenres: List<Pair<String, Int>> = emptyList(),
    /** 굿즈 단계에 보일 공식(보유·구매예정) · 2차창작 굿즈 */
    val goods: List<ReviewGoods> = emptyList(),
    /** 캐릭터 이름 → 사진 */
    val avatars: Map<String, String> = emptyMap(),
    /** 배신 굿즈 장르마다 확인할 굿즈 */
    val betrayReview: List<ExportPreview.BetrayReview> = emptyList(),
    /** 파일 기준 보유 현황 그래프(명조 → 배신 굿즈 장르 순) */
    val graphs: List<FileGraph> = emptyList()
)

data class ImportPlan(val sections: List<ImportSection>, val report: ImportReport = ImportReport()) {
    fun section(part: ImportPart) = sections.firstOrNull { it.part == part }
    /** 처음에 켜 둘 항목: 바뀌는 것. 배신 굿즈는 따로 물으므로 꺼 둔다. */
    val defaultSelection: Set<ImportPart>
        get() = sections.filter { it.changed && it.part != ImportPart.BETRAY }.map { it.part }.toSet()
}

object ImportPreviewer {

    /** 확인 화면에서 보일 사진 미리보기(파일 안 사진을 작게 줄인 것). 확인 창을 닫으면 지운다. */
    private val previewDir: File get() = File(App.appContext.cacheDir, "import_preview")

    /** 미리보기 사진을 지운다(확인 창을 닫을 때). */
    fun clearPreviews() {
        runCatching { previewDir.deleteRecursively() }
    }

    /** 파일 안 사진을 긴 변 [PREVIEW_PX] 정도로 줄여 미리보기 폴더에 둔다. 사진이 아니거나 깨졌으면 건너뛴다. */
    private fun savePreview(name: String, bytes: ByteArray) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_PX) sample *= 2
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return
            File(previewDir, name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
            bmp.recycle()
        }
    }

    /**
     * 확인 화면에서 쓸 사진 경로: 파일에서 만든 미리보기 → 이 기기에 같은 이름 사진 순. 둘 다 없으면 "".
     * 처음 불러오는 기기에는 사진이 아직 없어서, 미리보기가 없으면 사진이 안 보인다.
     */
    private fun previewPath(path: String?): String {
        val name = path.orEmpty().let { File(it).name }
        if (name.isEmpty()) return ""
        File(previewDir, name).takeIf { it.isFile }?.let { return it.absolutePath }
        return File(App.appContext.filesDir, name).takeIf { it.isFile }?.absolutePath.orEmpty()
    }

    private const val PREVIEW_PX = 256

    /**
     * 확인 화면에 실제로 보이는 사진 이름만: 비공식 굿즈 사진, 불러올 배신 굿즈 장르의 굿즈·캐릭터 사진.
     * [onlyGenre] 면 그 장르 것만. 아직 읽은 목록이 없거나 읽지 못하면 null(= 모든 사진을 줄인다).
     */
    internal fun previewWanted(texts: Map<String, String>, onlyGenre: String?): Set<String>? {
        if (texts.isEmpty()) return null
        val gson = Gson()
        val names = mutableSetOf<String>()
        fun add(path: String?) { path.orEmpty().let { File(it).name }.takeIf { it.isNotEmpty() }?.let { names += it } }
        if (onlyGenre == null) texts["fan_goods.json"]?.let { json ->
            val list = runCatching { gson.fromJson<List<FanGoodsBackup?>>(json, object : TypeToken<List<FanGoodsBackup?>>() {}.type) }.getOrNull()
                ?: return null
            list.forEach { add(it?.fanGoodsImgPath) }
        }
        texts["betray_goods.json"]?.let { json ->
            val all = runCatching { gson.fromJson(json, BetrayBackup::class.java) }.getOrNull() ?: return null
            val pick = if (onlyGenre != null) all.onlyGenre(onlyGenre) ?: return emptySet() else all
            pick.goods.orEmpty().forEach { add(it?.goodsImgPath) }
            pick.charas.orEmpty().forEach { add(it?.charaImgPath) }
        }
        return names
    }

    /**
     * [input] 의 백업을 읽어(사진은 세기만) 지금 데이터와 비교한다. 백업이 아니거나 깨졌으면 예외.
     * [betrayMerge] 면 배신 굿즈를 합치기(장르 복구)로 본다: 파일에 있는 장르만 바뀌거나 더해진다.
     */
    suspend fun inspect(
        input: InputStream,
        betrayMerge: Boolean = false,
        /** 장르 마이페이지의 데이터 입력: 이 이름의 배신 굿즈 장르만 본다(명조·다른 장르는 무시). 파일에 없으면 예외 */
        onlyGenre: String? = null,
        /** 파일 크기(모르면 0 이하). 읽은 만큼 [onProgress] 로 0~1 을 알린다 */
        totalBytes: Long = -1,
        onProgress: (Float) -> Unit = {}
    ): ImportPlan = withContext(Dispatchers.IO) {
        val texts = mutableMapOf<String, String>()
        var photoCount = 0
        val photoNames = mutableSetOf<String>()
        // 지난번 미리보기는 버리고 새로 만든다.
        clearPreviews()
        previewDir.mkdirs()
        // 미리보기를 만들 사진. 앱이 만든 백업은 목록(json)이 사진보다 앞에 있어서, 첫 사진을 만났을 때 정한다.
        var wanted: Set<String>? = null
        var seenImage = false
        var read = 0L
        var lastReported = -1
        // 읽은 바이트를 세어 게이지에 보인다(읽기가 대부분이라 0.9 까지, 비교가 나머지).
        val counting = object : java.io.FilterInputStream(input) {
            private fun count(n: Int) {
                if (n <= 0 || totalBytes <= 0) return
                read += n
                val pct = ((read.toDouble() / totalBytes) * 90).toInt().coerceIn(0, 90)
                if (pct != lastReported) { lastReported = pct; onProgress(pct / 100f) }
            }
            override fun read(): Int = super.read().also { if (it >= 0) count(1) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { count(it) }
        }
        ZipInputStream(counting).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                val name = parts.lastOrNull().orEmpty()
                when {
                    entry.isDirectory || name.isEmpty() -> Unit
                    parts.size >= 2 && parts[parts.size - 2] == "images" -> {
                        if (!seenImage) { seenImage = true; wanted = previewWanted(texts, onlyGenre) }
                        photoCount++
                        photoNames += name
                        // 처음 불러오는 기기에도 사진이 보이게 작게 줄인 미리보기를 만든다(사진 한 장씩만 메모리에 둔다).
                        // 이 기기에 같은 이름 사진이 이미 있으면(같은 기기에서 다시 불러오기 등) 그걸 보이고 줄이지 않는다.
                        // 확인 화면에 안 보이는 사진(다른 장르·시리즈·장르 배경 등)은 줄이지 않는다.
                        val show = wanted?.let { name in it } ?: true
                        if (show && name != "." && name != ".." && !File(App.appContext.filesDir, name).isFile) savePreview(name, zip.readBytes())
                    }
                    name.endsWith(".json") -> {
                        texts[name] = zip.bufferedReader().readText()
                        // 사진 뒤에 목록이 오는 파일(PC 에서 다시 묶은 zip 등)이면 이제부터는 모든 사진을 줄인다.
                        if (seenImage) wanted = null
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val known = listOf("official_gotten.json", "fan_goods.json", "favorite_charas.json", "betray_goods.json", BackupZip.FAN_SERIES)
        if (known.none { it in texts }) throw IllegalStateException("백업 파일이 아닙니다")
        val gson = Gson()
        if (onlyGenre != null) {
            // 이 장르와 이름이 같은 장르만 남기고, 그 밖의 내용(명조·다른 장르)은 파일에 없는 것처럼 본다.
            val betray = texts["betray_goods.json"]?.let {
                runCatching { gson.fromJson(it, BetrayBackup::class.java) }.getOrNull()
                    ?: throw IllegalStateException("백업 파일이 손상되었습니다 (betray_goods.json)")
            }
            val only = betray?.onlyGenre(onlyGenre)
                ?: throw IllegalStateException("이 파일에는 '$onlyGenre' 장르가 없어 불러올 수 없어요")
            texts.clear()
            texts["betray_goods.json"] = gson.toJson(only)
            // 사진 수도 이 장르가 쓰는 것만 센다(다른 장르·명조 사진은 불러오지 않는다).
            val used = (only.goods.orEmpty().map { it?.goodsImgPath } + only.charas.orEmpty().map { it?.charaImgPath } +
                only.genres.orEmpty().flatMap { listOf(it?.genreImgPath, it?.genreIconPath) } +
                only.series.orEmpty().flatMap { listOf(it?.seriesImgPath, it?.seriesIconPath) })
                .mapNotNull { p -> p?.let { File(it).name }?.takeIf { it.isNotEmpty() } }.toSet()
            photoCount = photoNames.count { it in used }
        }
        fun <T : Any> list(name: String, type: java.lang.reflect.Type): List<T>? = texts[name]?.let {
            runCatching { gson.fromJson<List<T?>?>(it, type)?.filterNotNull() }.getOrNull()
                ?: throw IllegalStateException("백업 파일이 손상되었습니다 ($name)")
        }

        val sections = mutableListOf<ImportSection>()
        val charaAvatars = App.charaDatabase.charaDao().getAll().associate { it.charaNm to it.charaUrl }
        val avatarOf: (String) -> String = { charaAvatars[it].orEmpty() }

        // ── 공식 굿즈 보유 현황 ──
        list<OfficialGoodsBackup>("official_gotten.json", object : TypeToken<List<OfficialGoodsBackup>>() {}.type)?.let { backups ->
            val current = App.database.goodsDao().getAll()
            val pairs = matchOfficialBackups(backups, current)
            val after = current.associate { it.goodsId to GoodsStatus.NOT_GOTTEN }.toMutableMap()
            pairs.forEach { (b, g) -> after[g.goodsId] = b.restoredStatus }
            fun summary(st: (Int) -> GoodsStatus): String {
                val list = current.map { st(it.goodsId) }
                return "보유 ${list.count { it == GoodsStatus.GOTTEN }} · 구매예정 ${list.count { it == GoodsStatus.PENDING }}"
            }
            val nowById = current.associate { it.goodsId to goodsStatusOf(it.goodsStatus) }
            val changedCount = current.count { nowById[it.goodsId] != after[it.goodsId] }
            val missing = backups.count { it.isOwned } - pairs.size
            // 캐릭터별 보유·구매예정 개수(세트 줄은 빼고 낱개만)
            val single = current.filter { it.goodsCategory != com.example.wowagoodsproject.component.CATEGORY_SET }
            fun count(st: GoodsStatus, of: (Int) -> GoodsStatus?) =
                countByChara(single.filter { (of(it.goodsId) ?: GoodsStatus.NOT_GOTTEN) == st }) { it.goodsChara }
            sections += ImportSection(
                rows = diffRows(count(GoodsStatus.GOTTEN) { nowById[it] }, count(GoodsStatus.GOTTEN) { after[it] }, avatarOf),
                pendingRows = diffRows(count(GoodsStatus.PENDING) { nowById[it] }, count(GoodsStatus.PENDING) { after[it] }, avatarOf),
                part = ImportPart.OFFICIAL,
                now = summary { nowById[it] ?: GoodsStatus.NOT_GOTTEN },
                file = summary { after[it] ?: GoodsStatus.NOT_GOTTEN },
                changed = changedCount > 0,
                details = listOfNotNull(
                    if (changedCount > 0) "상태가 바뀌는 굿즈 ${changedCount}개" else "바뀌는 굿즈가 없습니다",
                    if (missing > 0) "이 기기 공식 굿즈 목록에 없는 굿즈 ${missing}개는 건너뜀(굿즈 데이터를 업데이트한 뒤 다시 불러오면 들어가요)" else null
                )
            )
        }

        // ── 2차창작 굿즈 ──
        list<FanGoodsBackup>("fan_goods.json", object : TypeToken<List<FanGoodsBackup>>() {}.type)?.let { backups ->
            val current = App.fanDatabase.fanGoodsDao().getAll()
            fun key(series: String, chara: String, category: String, price: String, memo: String, status: String, img: String) =
                listOf(series.trim(), chara.trim(), category.trim(), price.trim(), memo.trim(), status, File(img).name).joinToString("\u0001")
            val now = current.map { key(it.fanGoodsSeries, it.fanGoodsChara, it.fanGoodsCategory, it.fanGoodsPrice, it.fanGoodsMemo, it.fanGoodsStatus, it.fanGoodsImgPath) }
            val fileEntities = backups.map { it.toEntity(it.fanGoodsImgPath.orEmpty()) }
            val file = fileEntities
                .map { key(it.fanGoodsSeries, it.fanGoodsChara, it.fanGoodsCategory, it.fanGoodsPrice, it.fanGoodsMemo, it.fanGoodsStatus, it.fanGoodsImgPath) }
            val (added, removed) = multisetDiff(now, file)
            // 굿즈 하나하나의 바뀜은 굿즈 확인 단계('바뀌는 굿즈')에서 보인다
            sections += ImportSection(
                rows = diffRows(countByChara(current) { it.fanGoodsChara }, countByChara(fileEntities) { it.fanGoodsChara }, avatarOf),
                part = ImportPart.FAN, now = "${current.size}개", file = "${backups.size}개", changed = added + removed > 0,
                details = if (added + removed == 0) listOf("내용이 같습니다")
                else listOfNotNull(
                    "지금 2차창작 굿즈는 모두 파일 것으로 바뀝니다",
                    if (added > 0) "새로 생기거나 바뀌는 굿즈 ${added}개" else null,
                    if (removed > 0) "없어지거나 바뀌는 굿즈 ${removed}개" else null
                )
            )
        }

        // ── 2차창작 시리즈 ──
        list<FanSeriesBackup>(BackupZip.FAN_SERIES, object : TypeToken<List<FanSeriesBackup>>() {}.type)?.let { backups ->
            val current = App.fanDatabase.fanGoodsDao().allSeries()
            val now = current.map { "${it.seriesNm}\u0001${File(it.seriesImgPath).name}" }.toSet()
            val file = backups.filter { !it.seriesNm.isNullOrBlank() }.map { "${it.seriesNm!!.trim()}\u0001${File(it.seriesImgPath.orEmpty()).name}" }.toSet()
            val nowNames = current.map { it.seriesNm }.toSet()
            val fileNames = backups.mapNotNull { it.seriesNm?.trim()?.takeIf { n -> n.isNotEmpty() } }.toSet()
            sections += ImportSection(
                ImportPart.FAN_SERIES, now = "${nowNames.size}개", file = "${fileNames.size}개", changed = now != file,
                details = if (now == file) listOf("내용이 같습니다") else listOfNotNull(
                    (fileNames - nowNames).takeIf { it.isNotEmpty() }?.let { "추가: ${it.joinToString(", ")}" },
                    (nowNames - fileNames).takeIf { it.isNotEmpty() }?.let { "없어짐: ${it.joinToString(", ")}" },
                    if ((fileNames - nowNames).isEmpty() && (nowNames - fileNames).isEmpty()) "사진이 바뀌는 시리즈가 있습니다" else null
                )
            )
        }

        // ── 선호 캐릭터 ──
        list<String>("favorite_charas.json", object : TypeToken<List<String>>() {}.type)?.let { names ->
            val file = names.map { it.trim() }.distinct()
            val now = App.charaDatabase.charaDao().getAll().filter { it.charaIsFavorite }
                .sortedWith(compareBy<CharaEntity> { it.charaFavoriteOrder }.thenBy { it.charaId }).map { it.charaNm }
            sections += ImportSection(
                favRows = favRows(now, file, App.charaDatabase.charaDao().getAll().map { it.charaNm }, avatarOf),
                part = ImportPart.FAVORITES, now = "${now.size}명", file = "${file.size}명", changed = now != file,
                details = if (now == file) listOf(now.joinToString(", ").ifEmpty { "없음" })
                else listOf("지금: ${now.joinToString(", ").ifEmpty { "없음" }}", "파일: ${file.joinToString(", ").ifEmpty { "없음" }}")
            )
        }

        // ── 배신 굿즈 ──
        texts["betray_goods.json"]?.let { json ->
            val backup = runCatching { gson.fromJson(json, BetrayBackup::class.java) }.getOrNull()
                ?: throw IllegalStateException("백업 파일이 손상되었습니다 (betray_goods.json)")
            sections += betraySection(backup, merge = betrayMerge)
        }

        onProgress(0.95f)
        val plan = ImportPlan(sections, buildReport(texts, photoCount, gson))
        onProgress(1f)
        plan
    }

    /** 파일 내용만으로 만든 보고서. 캐릭터 사진은 이 기기의 캐릭터 목록에서 이름으로 찾는다. */
    private suspend fun buildReport(texts: Map<String, String>, photos: Int, gson: Gson): ImportReport {
        val official = texts["official_gotten.json"]?.let {
            runCatching { gson.fromJson<List<OfficialGoodsBackup?>>(it, object : TypeToken<List<OfficialGoodsBackup?>>() {}.type) }.getOrNull()
        }.orEmpty().filterNotNull().filter { it.isOwned }
        val fan = texts["fan_goods.json"]?.let {
            runCatching { gson.fromJson<List<FanGoodsBackup?>>(it, object : TypeToken<List<FanGoodsBackup?>>() {}.type) }.getOrNull()
        }.orEmpty().filterNotNull()
        val favoriteNames = texts["favorite_charas.json"]?.let {
            runCatching { gson.fromJson<List<String?>>(it, object : TypeToken<List<String?>>() {}.type) }.getOrNull()
        }.orEmpty().filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val fanSeries = texts[BackupZip.FAN_SERIES]?.let {
            runCatching { gson.fromJson<List<FanSeriesBackup?>>(it, object : TypeToken<List<FanSeriesBackup?>>() {}.type) }.getOrNull()
        }.orEmpty().filterNotNull().count { !it.seriesNm.isNullOrBlank() }
        val betray = texts["betray_goods.json"]?.let { runCatching { gson.fromJson(it, BetrayBackup::class.java) }.getOrNull() }

        val avatars = App.charaDatabase.charaDao().getAll().associate { it.charaNm to it.charaUrl }
        val counts = linkedMapOf<String, IntArray>() // 이름 → [공식, 2차창작]
        fun add(field: String?, index: Int) = charaNames(field.orEmpty()).forEach { counts.getOrPut(it) { IntArray(2) }[index]++ }
        official.forEach { add(it.goodsChara, 0) }
        fan.forEach { add(it.fanGoodsChara, 1) }
        fun chara(name: String) = (counts[name] ?: IntArray(2)).let { ReportChara(name, avatars[name].orEmpty(), it[0], it[1]) }
        val genres = betray?.genres.orEmpty().filterNotNull().filter { (it.genreId ?: 0) > 0 }.distinctBy { it.genreId }
        val goodsByGenre = betray?.goods.orEmpty().filterNotNull().groupingBy { it.genreId ?: 0 }.eachCount()
        return ImportReport(
            officialGotten = official.count { it.restoredStatus == GoodsStatus.GOTTEN },
            officialPending = official.count { it.restoredStatus == GoodsStatus.PENDING },
            fanTotal = fan.size,
            fanSeries = fanSeries,
            photos = photos,
            favorites = favoriteNames.map { chara(it) },
            charas = counts.keys.map { chara(it) }.sortedWith(compareByDescending<ReportChara> { it.total }.thenBy { it.name }),
            betrayGenres = genres.map { it.genreNm.orEmpty() to (goodsByGenre[it.genreId] ?: 0) },
            goods = reviewGoodsOf(official, fan, "official_gotten.json" in texts, "fan_goods.json" in texts),
            avatars = avatars,
            betrayReview = betray?.let { withLocalChanges(betrayReviewOf(it)) }.orEmpty(),
            graphs = listOfNotNull(
                if (listOf("official_gotten.json", "fan_goods.json", "favorite_charas.json").any { it in texts }) wuwaGraph(official, fan, favoriteNames) else null
            ) + betray?.let { betrayGraphs(it) }.orEmpty()
        )
    }

    /** 파일대로 불러왔을 때의 명조 '보유 굿즈'(공식은 이 기기 굿즈 목록에 파일 상태를 입힌 것) */
    private suspend fun wuwaGraph(official: List<OfficialGoodsBackup>, fan: List<FanGoodsBackup>, favorites: List<String>): FileGraph {
        val local = App.database.goodsDao().getAll()
        val fileStatus = matchOfficialBackups(official, local).associate { (b, g) -> g.goodsId to b.restoredStatus }
        val after = local.map { it.copy(goodsStatus = (fileStatus[it.goodsId] ?: GoodsStatus.NOT_GOTTEN).name) }
        val gotten = after.filter { it.goodsCategory != com.example.wowagoodsproject.component.CATEGORY_SET && it.status == GoodsStatus.GOTTEN } +
            fan.map { it.toEntity("") }.filter { it.status == GoodsStatus.GOTTEN }
        return FileGraph(
            title = "보유 굿즈",
            gotten = gotten,
            charas = App.charaDatabase.charaDao().getAll(),
            favorites = favorites,
            collection = com.example.wowagoodsproject.component.charaCollection(after),
            collectionTitle = "선호 캐릭터 공식 수집현황"
        )
    }

    /** 배신 굿즈 장르마다 파일 기준 '보유 굿즈'(배신 굿즈 마이페이지와 같은 모양) */
    private fun betrayGraphs(b: BetrayBackup): List<FileGraph> {
        fun local(path: String?): String = previewPath(path)
        val genres = b.genres.orEmpty().filterNotNull().filter { (it.genreId ?: 0) > 0 }.distinctBy { it.genreId }.sortedBy { it.genreOrder ?: 0 }
        return genres.map { g ->
            val id = g.genreId!!
            val goods = b.goods.orEmpty().filterNotNull().filter { it.genreId == id }.map { it.toEntity(local(it.goodsImgPath)) }
            val favs = b.favorites.orEmpty().filterNotNull().filter { it.genreId == id && !it.charaNm.isNullOrBlank() }
                .map { com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity(id, it.charaNm!!.trim(), it.favoriteOrder ?: 0) }
            val charas = b.charas.orEmpty().filterNotNull().filter { it.genreId == id && !it.charaNm.isNullOrBlank() }
                .map { com.example.wowagoodsproject.db.betray.BetrayCharaEntity(id, it.charaNm!!.trim(), local(it.charaImgPath)) }
            val bc = com.example.wowagoodsproject.screen.betray.betrayCharas(goods, favs, charas)
            val gotten = goods.filter { it.status == GoodsStatus.GOTTEN }
            FileGraph(
                title = "보유 굿즈",
                genreName = g.genreNm.orEmpty(),
                gotten = gotten,
                charas = bc.mapIndexed { i, c -> c.asCharaEntity(i + 1) },
                favorites = bc.filter { it.favorite }.sortedWith(compareBy<com.example.wowagoodsproject.screen.betray.BetrayChara> { it.favoriteOrder }.thenBy { it.name }).map { it.name },
                collection = bc.associate { it.name to (it.gotten to it.total) },
                collectionTitle = "선호 캐릭터 수집률",
                breakdown = com.example.wowagoodsproject.screen.betray.charaKindBreakdown(gotten),
                betray = true
            )
        }
    }

    /** 파일의 배신 굿즈를 장르마다 확인 목록으로. 사진은 이 기기에 같은 파일이 있을 때만 보인다. */
    private fun betrayReviewOf(b: BetrayBackup): List<ExportPreview.BetrayReview> {
        fun local(path: String?): String = previewPath(path)
        val genres = b.genres.orEmpty().filterNotNull().filter { (it.genreId ?: 0) > 0 }.distinctBy { it.genreId }
        val ids = genres.mapNotNull { it.genreId }.toSet()
        return ExportPreview.betrayReviewOf(
            genres = genres.sortedBy { it.genreOrder ?: 0 }.map { it.genreId!! to it.genreNm.orEmpty() },
            goods = b.goods.orEmpty().filterNotNull().filter { it.genreId in ids }.map { it.toEntity(local(it.goodsImgPath)) },
            favorites = b.favorites.orEmpty().filterNotNull().filter { it.genreId in ids && !it.charaNm.isNullOrBlank() }
                .map { com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity(it.genreId!!, it.charaNm!!.trim(), it.favoriteOrder ?: 0) },
            charas = b.charas.orEmpty().filterNotNull().filter { it.genreId in ids && !it.charaNm.isNullOrBlank() }
                .map { com.example.wowagoodsproject.db.betray.BetrayCharaEntity(it.genreId!!, it.charaNm!!.trim(), local(it.charaImgPath)) }
        )
    }

    /** 공식은 이 기기 굿즈와 짝지어(사진·가격은 기기 데이터) 되살아날 것만, 2차창작은 파일 그대로. 사진은 이 기기에 같은 파일이 있을 때만. */
    private suspend fun reviewGoodsOf(
        official: List<OfficialGoodsBackup>,
        fan: List<FanGoodsBackup>,
        /** 파일에 공식 보유 목록·비공식 목록이 있는지(없으면 그 부분은 불러와도 그대로라 없어지는 굿즈가 없다) */
        officialInFile: Boolean,
        fanInFile: Boolean
    ): List<ReviewGoods> {
        val local = App.database.goodsDao().getAll()
        val pairs = matchOfficialBackups(official, local)
        val matched = pairs.map { it.second.goodsId }.toSet()
        val officialRows = pairs.map { (b, g) ->
            val before = goodsStatusOf(g.goodsStatus)
            val beforeQty = reviewQuantity(before, g.goodsQuantity)
            val afterQty = (b.goodsQuantity ?: 1).coerceAtLeast(1)
            // 상태가 같아도 수량이 다르면 바뀌는 굿즈
            val changed = before != b.restoredStatus || beforeQty != reviewQuantity(b.restoredStatus, afterQty)
            ReviewGoods(
                official = true, series = g.goodsSeries, chara = g.goodsChara, category = g.goodsCategory,
                price = g.goodsPrice, memo = b.goodsMemo.orEmpty(), status = b.restoredStatus,
                quantity = afterQty, img = g.imgPath,
                change = if (changed) before to b.restoredStatus else null,
                beforeQuantity = beforeQty
            )
        } + local
            // 지금 보유·구매예정인데 파일에서 짝을 못 찾은 굿즈: 불러오면 미보유가 된다(세트 줄은 구성품에 따라 정해지므로 뺀다)
            .filter { officialInFile && it.goodsId !in matched && goodsStatusOf(it.goodsStatus) != GoodsStatus.NOT_GOTTEN && it.goodsCategory != com.example.wowagoodsproject.component.CATEGORY_SET }
            .map { g ->
                ReviewGoods(
                    official = true, series = g.goodsSeries, chara = g.goodsChara, category = g.goodsCategory,
                    price = g.goodsPrice, memo = g.goodsMemo, status = GoodsStatus.NOT_GOTTEN, quantity = 1, img = g.imgPath,
                    change = goodsStatusOf(g.goodsStatus) to GoodsStatus.NOT_GOTTEN,
                    beforeQuantity = g.goodsQuantity.coerceAtLeast(1)
                )
            }
        // 비공식: 파일 것으로 통째로 바뀐다. 내용(시리즈·캐릭터·카테고리·가격·메모·사진)으로 짝지어 지금 → 불러온 뒤를 붙인다.
        fun fanKey(series: String, chara: String, category: String, price: String, memo: String, img: String) =
            listOf(series.trim(), chara.trim(), category.trim(), price.trim(), memo.trim(), File(img).name).joinToString("\u0001")
        val fileFan = fan.map { f ->
            val e = f.toEntity("")
            ReviewGoods(
                official = false, series = e.fanGoodsSeries, chara = e.fanGoodsChara, category = e.fanGoodsCategory,
                price = e.fanGoodsPrice, memo = e.fanGoodsMemo, status = e.status, quantity = e.quantity,
                img = previewPath(f.fanGoodsImgPath)
            ) to fanKey(e.fanGoodsSeries, e.fanGoodsChara, e.fanGoodsCategory, e.fanGoodsPrice, e.fanGoodsMemo, f.fanGoodsImgPath.orEmpty())
        }
        // 파일에 비공식 목록이 없으면 지금 비공식 굿즈는 그대로라 비교하지 않는다.
        val fanRows = if (!fanInFile) emptyList() else withChanges(
            file = fileFan,
            now = App.fanDatabase.fanGoodsDao().getAll(),
            key = { fanKey(it.fanGoodsSeries, it.fanGoodsChara, it.fanGoodsCategory, it.fanGoodsPrice, it.fanGoodsMemo, it.fanGoodsImgPath) },
            status = { it.status },
            quantity = { it.fanGoodsQuantity },
            gone = {
                ReviewGoods(
                    official = false, series = it.fanGoodsSeries, chara = it.fanGoodsChara, category = it.fanGoodsCategory,
                    price = it.fanGoodsPrice, memo = it.fanGoodsMemo, status = it.status, quantity = it.fanGoodsQuantity,
                    img = it.fanGoodsImgPath, change = it.status to null
                )
            }
        )
        return officialRows + fanRows
    }

    /**
     * 배신 굿즈 장르마다 확인 목록에 지금 → 불러온 뒤를 붙인다. 파일 장르와 이름이 같은 이 기기 장르의 굿즈는 파일 것으로 바뀌므로
     * 내용(시리즈·캐릭터·카테고리·가격·메모·종류)으로 짝짓고, 파일에 없는 이 기기 굿즈는 '없어지는 굿즈'로 더한다.
     */
    private suspend fun withLocalChanges(reviews: List<ExportPreview.BetrayReview>): List<ExportPreview.BetrayReview> {
        val dao = App.betrayDatabase.betrayDao()
        val genres = dao.genres()
        return reviews.map { r ->
            val local = genres.firstOrNull { it.genreNm.trim().equals(r.genreName.trim(), ignoreCase = true) }
            val now = local?.let { dao.goodsOf(it.genreId) }.orEmpty()
            fun key(official: Boolean, series: String, chara: String, category: String, price: String, memo: String) =
                listOf(official.toString(), series.trim(), chara.trim(), category.trim(), price.trim(), memo.trim()).joinToString("\u0001")
            r.copy(
                goods = withChanges(
                    file = r.goods.map { it to key(it.official, it.series, it.chara, it.category, it.price, it.memo) },
                    now = now,
                    key = { key(it.kind == com.example.wowagoodsproject.db.betray.BetrayKind.OFFICIAL, it.goodsSeries, it.goodsChara, it.goodsCategory, it.goodsPrice, it.goodsMemo) },
                    status = { it.status },
                    quantity = { it.goodsQuantity },
                    gone = {
                        ReviewGoods(
                            it.kind == com.example.wowagoodsproject.db.betray.BetrayKind.OFFICIAL,
                            it.goodsSeries, it.goodsChara, it.goodsCategory, it.goodsPrice, it.goodsMemo, it.status, it.goodsQuantity, it.goodsImgPath,
                            change = it.status to null
                        )
                    }
                ),
                // 없어지는 굿즈의 캐릭터 사진도 보이게 이 기기 장르 사진을 더한다
                avatars = r.avatars + local?.let { l ->
                    com.example.wowagoodsproject.screen.betray.betrayCharas(now, dao.favoritesOf(l.genreId), dao.charasOf(l.genreId)).associate { it.name to it.cover }
                }.orEmpty().filterKeys { it !in r.avatars }
            )
        }
    }

    /**
     * 배신 굿즈 비교. [merge] 면 장르 복구처럼 파일에 있는 장르만 바꾸거나 더하는 것으로 본다(파일이 전체 백업이어도).
     */
    suspend fun betraySection(backup: BetrayBackup, merge: Boolean): ImportSection {
        val dao = App.betrayDatabase.betrayDao()
        val localGenres = dao.genres()
        val localGoods = dao.allGoods().groupBy { it.genreId }
        val fileGenres = backup.genres.orEmpty().filterNotNull().filter { (it.genreId ?: 0) > 0 }.distinctBy { it.genreId }
        val fileGoods = backup.goods.orEmpty().filterNotNull().groupBy { it.genreId ?: 0 }
        val partial = merge || backup.partial == true
        fun goodsKey(series: String?, chara: String?, category: String?, kind: String?, status: String?, price: String?, memo: String?) =
            listOf(series.orEmpty().trim(), charaNames(chara.orEmpty()).joinToString(","), category.orEmpty().trim(), kind.orEmpty(),
                status.orEmpty(), price.orEmpty().trim(), memo.orEmpty().trim()).joinToString("\u0001")
        val details = mutableListOf<String>()
        val genreDiffs = mutableListOf<GenreDiff>()
        var changed = false
        val localFavs = dao.allFavorites().groupBy { it.genreId }
        val localCharas = dao.allCharas().groupBy { it.genreId }
        val fileFavs = backup.favorites.orEmpty().filterNotNull().filter { !it.charaNm.isNullOrBlank() }.groupBy { it.genreId ?: 0 }
        /** 장르의 캐릭터 사진(이 기기 기준). 새 장르면 비어 있다. */
        fun covers(localId: Int?): Map<String, String> = localId?.let { id ->
            com.example.wowagoodsproject.screen.betray.betrayCharas(localGoods[id].orEmpty(), localFavs[id].orEmpty(), localCharas[id].orEmpty())
                .associate { it.name to it.cover }
        }.orEmpty()
        fun favNames(list: List<com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity>) =
            list.sortedBy { it.favoriteOrder }.map { it.charaNm }
        fileGenres.forEach { g ->
            val name = g.genreNm.orEmpty().trim()
            val incoming = fileGoods[g.genreId].orEmpty()
            val same = localGenres.firstOrNull { it.genreNm.trim().equals(name, ignoreCase = true) }
            val cover = covers(same?.genreId)
            val beforeGoods = same?.let { localGoods[it.genreId] }.orEmpty()
            val afterFavs = fileFavs[g.genreId].orEmpty().sortedBy { it.favoriteOrder ?: 0 }.map { it.charaNm!!.trim() }.distinct()
            val beforeFavs = same?.let { favNames(localFavs[it.genreId].orEmpty()) }.orEmpty()
            val rowsOf = diffRows(countByChara(beforeGoods) { it.goodsChara }, countByChara(incoming) { it.goodsChara.orEmpty() }) { cover[it].orEmpty() }
            val favOf = favRows(beforeFavs, afterFavs, (cover.keys + countByChara(incoming) { it.goodsChara.orEmpty() }.keys).toList()) { cover[it].orEmpty() }
            genreDiffs += GenreDiff(
                name = name,
                note = if (same == null) "새 장르 · 굿즈 ${incoming.size}개" else "굿즈 ${beforeGoods.size}개 → ${incoming.size}개",
                rows = rowsOf,
                favRows = favOf
            )
            if (same == null) {
                changed = true
                details += "$name: 새 장르 (굿즈 ${incoming.size}개)"
            } else {
                val now = localGoods[same.genreId].orEmpty().map { goodsKey(it.goodsSeries, it.goodsChara, it.goodsCategory, it.goodsKind, it.goodsStatus, it.goodsPrice, it.goodsMemo) }
                val file = incoming.map { goodsKey(it.goodsSeries, it.goodsChara, it.goodsCategory, it.goodsKind, it.goodsStatus, it.goodsPrice, it.goodsMemo) }
                val (a, r) = multisetDiff(now, file)
                if (a + r > 0) changed = true
                details += if (a + r == 0) "$name: 굿즈 ${now.size}개 그대로"
                else "$name: 굿즈 ${now.size}개 → ${file.size}개 (바뀌는 굿즈 ${maxOf(a, r)}개)"
            }
        }
        if (!partial) {
            val fileNames = fileGenres.map { it.genreNm.orEmpty().trim().lowercase() }.toSet()
            localGenres.filter { it.genreNm.trim().lowercase() !in fileNames }.forEach {
                changed = true
                details += "${it.genreNm}: 파일에 없어 지워집니다 (굿즈 ${localGoods[it.genreId].orEmpty().size}개)"
                val cover = covers(it.genreId)
                genreDiffs += GenreDiff(
                    name = it.genreNm,
                    note = "파일에 없어 지워집니다",
                    rows = diffRows(countByChara(localGoods[it.genreId].orEmpty()) { g -> g.goodsChara }, emptyMap()) { n -> cover[n].orEmpty() },
                    favRows = favRows(favNames(localFavs[it.genreId].orEmpty()), emptyList()) { n -> cover[n].orEmpty() }
                )
            }
        }
        details.add(0, if (partial) "파일에 있는 장르만 바뀌거나 더해지고, 다른 장르는 그대로입니다" else "배신 굿즈 전체가 파일 것으로 바뀝니다")
        return ImportSection(
            ImportPart.BETRAY,
            now = "장르 ${localGenres.size}개 · 굿즈 ${localGoods.values.sumOf { it.size }}개",
            file = "장르 ${fileGenres.size}개 · 굿즈 ${fileGoods.filterKeys { k -> fileGenres.any { it.genreId == k } }.values.sumOf { it.size }}개",
            changed = changed,
            details = details,
            genres = genreDiffs
        )
    }

    /** 장르 복구용: 파일의 배신 굿즈만 비교(합치기). 배신 굿즈가 없으면 null */
    suspend fun inspectGenres(input: InputStream): ImportSection? = withContext(Dispatchers.IO) {
        var json: String? = null
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/').split('/').lastOrNull().orEmpty()
                if (!entry.isDirectory && name == "betray_goods.json") json = zip.bufferedReader().readText()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val backup = json?.let { runCatching { Gson().fromJson(it, BetrayBackup::class.java) }.getOrNull() } ?: return@withContext null
        if (!backup.hasData) return@withContext null
        betraySection(backup, merge = true)
    }

    /** 두 목록을 중복까지 세어 견줘 (파일에만 있는 수, 지금에만 있는 수) */
    private fun multisetDiff(now: List<String>, file: List<String>): Pair<Int, Int> {
        val counts = now.groupingBy { it }.eachCount().toMutableMap()
        var added = 0
        file.forEach { k ->
            val c = counts[k] ?: 0
            if (c > 0) counts[k] = c - 1 else added++
        }
        return added to counts.values.sum()
    }
}

/**
 * 불러오기 전 확인 시트: 항목마다 체크 · 지금 → 파일 · 바뀌는 내용. 고른 항목만 불러온다.
 */
@Composable
fun ImportPreviewSheet(
    plan: ImportPlan,
    onImport: (Set<ImportPart>) -> Unit,
    onDismiss: () -> Unit,
    title: String = "불러올 내용 확인",
    /** 배신 굿즈 줄의 이름 */
    betrayTitle: String = "배신 굿즈도 함께 불러오기",
    /** 처음에 켜 둘 항목 */
    initialSelection: Set<ImportPart> = plan.defaultSelection
) {
    val parts = remember(plan) { plan.sections.map { it.part }.toSet() }
    androidx.compose.runtime.DisposableEffect(plan) { onDispose { ImportPreviewer.clearPreviews() } }
    // 시리즈는 따로 보여 주지 않고 비공식 굿즈를 따라간다(굿즈에 시리즈 이름이 들어 있다). 굿즈 없이 시리즈만 든 파일이면 따로 둔다.
    val seriesFollowsFan = ImportPart.FAN in parts && ImportPart.FAN_SERIES in parts
    fun linked(set: Set<ImportPart>): Set<ImportPart> =
        if (!seriesFollowsFan) set else if (ImportPart.FAN in set) set + ImportPart.FAN_SERIES else set - ImportPart.FAN_SERIES
    var selected by remember(plan) {
        val initial = initialSelection.filter { it in parts }.toSet()
        mutableStateOf(linked(if (seriesFollowsFan && ImportPart.FAN_SERIES in initial) initial + ImportPart.FAN else initial))
    }
    val r = plan.report
    val goodsParts = remember(plan) { listOf(ImportPart.OFFICIAL, ImportPart.FAN).filter { it in parts }.toSet() }
    val allCharas = remember(plan) { reviewCharas(r.goods) }
    val betraySteps = remember(plan) { if (ImportPart.BETRAY in parts) r.betrayReview else emptyList() }
    val steps = remember(plan) {
        buildList {
            add(STEP_FILE)
            if (ImportPart.FAVORITES in parts) add(STEP_FAVORITES)
            if (goodsParts.isNotEmpty() && r.goods.isNotEmpty()) add(STEP_GOODS)
            betraySteps.forEach { add("배신 · ${it.genreName} 굿즈") }
            add(STEP_CONFIRM)
        }
    }
    // 배신 굿즈는 장르마다 굿즈를 보고 캐릭터를 모두 확인해야 불러온다(장르 번호 → 확인한 캐릭터).
    val betrayAll = remember(plan) { r.betrayReview.associate { it.genreId to reviewCharas(it.goods) } }
    var betrayChecked by remember(plan) {
        mutableStateOf(if (ImportPart.BETRAY in selected) betrayAll else emptyMap())
    }
    fun betrayDone(m: Map<Int, Set<String>>) = betrayAll.all { (id, all) -> m[id].orEmpty().containsAll(all) }
    fun setBetrayChecked(m: Map<Int, Set<String>>) {
        betrayChecked = m
        if (ImportPart.BETRAY in parts) selected = if (betrayDone(m)) selected + ImportPart.BETRAY else selected - ImportPart.BETRAY
    }
    var step by remember(plan) { mutableIntStateOf(0) }
    // 굿즈 단계에서 확인한 캐릭터. 모두 확인해야 공식·2차창작 굿즈를 불러온다.
    var checkedCharas by remember(plan) { mutableStateOf(if (goodsParts.isNotEmpty() && selected.containsAll(goodsParts)) allCharas else emptySet()) }
    fun setChecked(set: Set<String>) {
        checkedCharas = set
        selected = linked(if (set.containsAll(allCharas)) selected + goodsParts else selected - goodsParts)
    }
    fun toggle(part: ImportPart, on: Boolean) {
        selected = linked(if (on) selected + part else selected - part)
        if (part in goodsParts) checkedCharas = if (selected.containsAll(goodsParts)) allCharas else emptySet()
        if (part == ImportPart.BETRAY) betrayChecked = if (on) betrayAll else emptyMap()
    }
    WizardScreen(title = title, onDismiss = onDismiss) {
        WizardFrame(
            steps = steps,
            step = step,
            onStep = { step = it },
            nextEnabled = step != steps.lastIndex || selected.isNotEmpty(),
            finishLabel = if (selected.isEmpty()) "불러올 항목을 고르세요"
            else "선택한 항목 불러오기 (${selected.count { !(seriesFollowsFan && it == ImportPart.FAN_SERIES) }})",
            onFinish = { onImport(selected) }
        ) {
            when (steps[step]) {
                STEP_FILE -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    importReport(r, selected, available = parts) { part, on -> toggle(part, on) }
                }
                STEP_FAVORITES -> {
                    StepTitle("명조", "선호 캐릭터", betray = false)
                    val sec = plan.section(ImportPart.FAVORITES)!!
                    FavoritesReviewStep(
                        favorites = r.favorites.map { it.name to it.avatar },
                        note = null,
                        confirmText = "이 선호 캐릭터 목록이 맞아요",
                        checked = ImportPart.FAVORITES in selected,
                        onCheck = { toggle(ImportPart.FAVORITES, it) }
                    )
                }
                STEP_GOODS -> {
                    StepTitle("명조", "굿즈", betray = false)
                    GoodsReviewStep(
                    goods = r.goods,
                    avatars = r.avatars,
                    checked = checkedCharas,
                    onCheck = { name, on -> setChecked(if (on) checkedCharas + name else checkedCharas - name) },
                    onCheckAll = { setChecked(allCharas) },
                    importing = true
                )
                }
                STEP_CONFIRM -> {
                    // 항목 제목·표 머리(Before / After)는 위에 붙어 있고 줄만 스크롤된다.
                    // 체크는 제목 줄을 눌러야만 바뀐다(스크롤하다 표를 건드려 체크가 풀리지 않게).
                    val openOthers = remember(plan) { mutableStateMapOf<String, Boolean>() }
                    val shown = plan.sections.filterNot { seriesFollowsFan && it.part == ImportPart.FAN_SERIES }
                    val surface = MaterialTheme.colorScheme.surfaceContainerLow // 마법사 배경과 같은 색(붙은 머리가 비치지 않게)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        shown.forEachIndexed { index, sec ->
                            val isBetray = sec.part == ImportPart.BETRAY
                            val title = if (isBetray) betrayTitle else sec.part.title
                            val checked = sec.part in selected
                            // 명조 / 배신 굿즈 묶음 머리
                            if (index == 0 || (shown[index - 1].part == ImportPart.BETRAY) != isBetray) {
                                item(key = "__group_${sec.part}") { GroupHeader(if (isBetray) "배신 굿즈" else "명조", isBetray) }
                            }
                            stickyHeader(key = "t_${sec.part}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(surface)
                                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { on -> toggle(sec.part, on) })
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GreenCheck(checked, Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(
                                        if (sec.changed) "변경 있음" else "같음",
                                        style = AppStyles.textCardSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sec.changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            item(key = "s_${sec.part}") {
                                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                    // 지금 / 파일 요약을 Before / After 두 칸으로(한 줄이면 글자가 잘린다)
                                    SummaryBeforeAfter(sec.now, sec.file)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // 표로 보이는 내용은 글 설명에서 뺀다(선호 캐릭터 이름 목록, 배신 굿즈 장르별 줄).
                                    // 설명글은 빼고, 불러와도 들어가지 않는 굿즈가 있다는 경고만 남긴다.
                                    sec.details.filter { "건너뜀" in it }.forEach { d ->
                                        Text(d, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            /** 표 하나: 머리(항목 제목 + Before / After)는 위에 붙고 줄만 스크롤된다. */
                            fun <T> table(key: String, label: String, rows: List<T>, row: @Composable (T) -> Unit) {
                                if (rows.isEmpty()) return
                                stickyHeader(key = "h_$key") {
                                    Column(modifier = Modifier.fillMaxWidth().background(surface).padding(top = 6.dp)) {
                                        // 붙어 있는 머리에도 체크 상태를 보여, 스크롤하다 실수로 풀렸는지 바로 알 수 있게 한다(눌러서 바꿀 수도 있다).
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { on -> toggle(sec.part, on) })
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            GreenCheck(checked, Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(title, style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold, color = groupColor(isBetray), maxLines = 1, modifier = Modifier.weight(1f))
                                            Text(
                                                if (checked) "불러옴" else "불러오지 않음",
                                                style = AppStyles.textCardSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (checked) AppStyles.colorGotten else MaterialTheme.colorScheme.error
                                            )
                                        }
                                        DiffHeader(label)
                                    }
                                }
                                items(rows.size, key = { "r_${key}_$it" }) { i -> row(rows[i]) }
                            }
                            /** 선호 표: 지금 선호이거나 바뀌는 캐릭터만 보이고, 나머지는 '기타'로 접는다. */
                            fun favTable(key: String, label: String, rows: List<FavRow>) {
                                val main = rows.filter { it.before || it.after }
                                val others = rows.filter { !it.before && !it.after }
                                table(key, label, main) { FavRowItem(it) }
                                if (others.isEmpty()) return
                                val open = openOthers[key] == true
                                item(key = "o_$key") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openOthers[key] = !open }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("기타 ${others.size}명", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Icon(
                                            if (open) androidx.compose.material.icons.Icons.Filled.ExpandLess else androidx.compose.material.icons.Icons.Filled.ExpandMore,
                                            contentDescription = if (open) "기타 접기" else "기타 펼치기",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (open) items(others.size, key = { "or_${key}_$it" }) { i -> FavRowItem(others[i]) }
                            }
                            when (sec.part) {
                                ImportPart.OFFICIAL -> {
                                    table("off_g", "보유", sec.rows) { DiffRowItem(it) }
                                    table("off_p", "구매예정", sec.pendingRows) { DiffRowItem(it) }
                                }
                                ImportPart.FAN -> {
                                    table("fan_c", "캐릭터별 굿즈 수", sec.rows) { DiffRowItem(it) }
                                }
                                ImportPart.FAVORITES -> favTable("fav", "선호", sec.favRows)
                                ImportPart.BETRAY -> sec.genres.forEachIndexed { gi, g ->
                                    item(key = "g_$gi") {
                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = groupColor(true), modifier = Modifier.weight(1f))
                                            Text(g.note, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    table("b${gi}_d", "${g.name} 굿즈", g.rows) { DiffRowItem(it) }
                                    favTable("b${gi}_f", "${g.name} 선호 캐릭터", g.favRows)
                                }
                                ImportPart.FAN_SERIES -> Unit
                            }
                            item(key = "e_${sec.part}") {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                else -> {
                    // 장르 단계는 '불러올 항목 확인' 바로 앞에 장르 순서대로 놓인다.
                    val review = betraySteps.getOrNull(step - (steps.lastIndex - betraySteps.size))
                    if (review != null) {
                        val id = review.genreId
                        val mine = betrayChecked[id].orEmpty()
                        StepTitle("배신 굿즈", "${review.genreName} 굿즈", betray = true)
                        key(id) {
                            GoodsReviewStep(
                                goods = review.goods,
                                avatars = review.avatars,
                                checked = mine,
                                onCheck = { name, on -> setBetrayChecked(betrayChecked + (id to (if (on) mine + name else mine - name))) },
                                onCheckAll = { setBetrayChecked(betrayChecked + (id to betrayAll[id].orEmpty())) },
                                importing = true
                            )
                        }
                    }
                }
            }
        }
    }
}

internal const val STEP_FILE = "파일 내용"
internal const val STEP_FAVORITES = "명조 선호 캐릭터"
internal const val STEP_GOODS = "명조 굿즈"
internal const val STEP_CONFIRM = "불러올 항목 확인"

/** 보고서: 요약 숫자 칸 · 선호 캐릭터(사진) · 캐릭터별 공식/2차창작 · 배신 굿즈 장르 */
private fun androidx.compose.foundation.lazy.LazyListScope.importReport(
    r: ImportReport,
    selected: Set<ImportPart>,
    /** 파일에 있어 고를 수 있는 항목 */
    available: Set<ImportPart>,
    onToggle: (ImportPart, Boolean) -> Unit
) {
    item(key = "__report_title") {
        Text("파일 내용", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        // (이름, 숫자, 이 칸을 체크하면 불러올 항목). 사진은 굿즈를 따라가므로 체크가 없다.
        val tiles = listOfNotNull(
            if (r.officialGotten + r.officialPending > 0) Triple("공식 보유", "${r.officialGotten}", ImportPart.OFFICIAL) else null,
            if (r.officialPending > 0) Triple("공식 구매예정", "${r.officialPending}", ImportPart.OFFICIAL) else null,
            if (r.fanTotal > 0) Triple("비공식", "${r.fanTotal}", ImportPart.FAN) else null,
            if (r.favorites.isNotEmpty()) Triple("선호 캐릭터", "${r.favorites.size}명", ImportPart.FAVORITES) else null,
            if (r.betrayGenres.isNotEmpty()) Triple("배신 굿즈 장르", "${r.betrayGenres.size}", ImportPart.BETRAY) else null,
            if (r.photos > 0) Triple("사진", "${r.photos}장", null) else null
        )
        // 숫자 칸: 한 줄에 셋씩
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 넓은 화면(가로 태블릿)은 숫자 칸을 한 줄에 더 놓는다
            val perRow = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 1000) 6 else 3
            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value, part) ->
                        val canPick = part != null && part in available
                        val checked = canPick && part in selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .then(
                                    if (canPick) Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(part!!, it) })
                                    else Modifier
                                )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(label, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canPick) {
                                Icon(
                                    imageVector = if (checked) androidx.compose.material.icons.Icons.Filled.CheckCircle
                                    else androidx.compose.material.icons.Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = if (checked) "$label 불러오기 켜짐" else "$label 불러오기 꺼짐",
                                    tint = if (checked) AppStyles.colorGotten else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                                )
                            }
                        }
                    }
                    repeat(perRow - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
    // 파일대로 불러왔을 때의 보유 현황(마이페이지 '보유 굿즈'와 같은 모양)
    r.graphs.forEachIndexed { i, g ->
        if (i == 0 || r.graphs[i - 1].betray != g.betray) {
            item(key = "group_$i") { GroupHeader(if (g.betray) "배신 굿즈" else "명조", g.betray) }
        }
        item(key = "graph_$i") { FileGraphCard(g) }
    }
}

@Composable
private fun ReportLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

/** 고른 파일의 크기(모르면 -1). 불러오기 게이지에 쓴다. */
internal fun fileSizeOf(context: android.content.Context, uri: android.net.Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
    } ?: -1L
}.getOrDefault(-1L)
