package com.example.wowagoodsproject.screen.mypage

import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * 추출하기 전에 보여 줄 백업 내용 요약. [BackupZip.write] 가 담는 것과 같은 DB 에서 센다.
 *
 * 굿즈 수는 앱의 다른 화면과 같이 세트 줄을 빼고 낱개만 센다.
 * 굿즈 데이터를 받지 않은 채 추출하면 보유 기록이 하나도 없는 백업이 되므로 [goodsDataMissing] 으로 알린다.
 */
data class ExportPreview(
    /** 기기에 있는 공식 굿즈 데이터 줄 수(세트 포함). 백업에는 이 줄이 모두 들어간다. */
    val officialTotal: Int,
    val officialGotten: Int,
    val officialPending: Int,
    val fanTotal: Int,
    val fanGotten: Int,
    val fanPending: Int,
    /** 백업에 들어갈 2차창작 사진 수와 크기 */
    val photoCount: Int,
    val photoBytes: Long,
    /** 선호 우선순위 순 */
    val favorites: List<String>,
    /** 보유·구매예정이 많은 순. 명조(공식·2차창작)만 센다. */
    val byChara: List<CharaCount>,
    /** 배신 굿즈(다른 장르) 장르 수·굿즈 수·그중 보유 */
    val betrayGenres: Int = 0,
    val betrayGoods: Int = 0,
    val betrayGotten: Int = 0,
    /** 2차창작 시리즈 수(등록한 것) */
    val fanSeries: Int = 0,
    /** 2차창작 시리즈 이름(이름순) */
    val fanSeriesNames: List<String> = emptyList(),
    /** 배신 굿즈 선호 캐릭터·직접 추가한 캐릭터·등록한 시리즈(장르 이름 기준) */
    val betrayDetail: BetrayDetail = BetrayDetail()
) {
    /** 확인 단계에 보일 공식(보유·구매예정) · 2차창작 굿즈. 파일 검증(equals)에는 넣지 않는다 */
    var reviewGoods: List<ReviewGoods> = emptyList()
        private set
    /** 캐릭터 이름 → 사진 */
    var avatars: Map<String, String> = emptyMap()
        private set
    /** 확인 단계에 보일 배신 굿즈(장르마다 한 단계). 파일 검증(equals)에는 넣지 않는다 */
    var betrayReview: List<BetrayReview> = emptyList()
        internal set

    /** 배신 굿즈 한 장르의 확인 목록. 캐릭터 이름은 장르마다 따로라 장르별로 나눠 확인한다. */
    data class BetrayReview(val genreId: Int, val genreName: String, val goods: List<ReviewGoods>, val avatars: Map<String, String>)

    /**
     * 배신 굿즈에서 개수만으로는 못 거르는 것. 장르 번호는 기기마다 달라질 수 있어 장르 이름으로 묶는다.
     * @property favorites "장르|캐릭터" 를 장르 이름순 · 그 안은 선호 순서대로(순서도 비교한다)
     * @property charas "장르|캐릭터" 이름순
     * @property series "장르|시리즈" 이름순
     */
    data class BetrayDetail(
        val favorites: List<String> = emptyList(),
        val charas: List<String> = emptyList(),
        val series: List<String> = emptyList()
    ) {
        companion object {
            /** genreName: 장르 번호 → 이름. favorites 는 (장르 번호, 이름, 순서) */
            fun of(
                genreName: Map<Int, String>,
                favorites: List<Triple<Int, String, Int>>,
                charas: List<Pair<Int, String>>,
                series: List<Pair<Int, String>>
            ) = BetrayDetail(
                favorites = favorites.filter { it.first in genreName }
                    .sortedWith(compareBy<Triple<Int, String, Int>> { genreName.getValue(it.first) }.thenBy { it.third }.thenBy { it.second })
                    .map { "${genreName.getValue(it.first)}|${it.second}" },
                charas = charas.filter { it.first in genreName }.map { "${genreName.getValue(it.first)}|${it.second}" }.sorted(),
                series = series.filter { it.first in genreName }.map { "${genreName.getValue(it.first)}|${it.second}" }.sorted()
            )
        }
    }

    data class CharaCount(val name: String, val gotten: Int, val pending: Int) {
        val total get() = gotten + pending
    }

    /** 추출 완료 알림에 쓰는 한 줄 요약 */
    fun summary(): String = "공식 보유 $officialGotten · 구매예정 $officialPending, 2차창작 $fanTotal, 선호 캐릭터 ${favorites.size}명" +
        if (betrayGenres > 0) ", 배신 굿즈 $betrayGoods" else ""

    /**
     * [written](만든 zip 을 다시 읽은 것)과 다른 칸의 이름. 비어 있으면 파일이 기기 데이터 그대로다.
     * 사진 크기는 압축 전 크기로 비교한다.
     */
    fun differencesFrom(written: ExportPreview): List<String> = listOfNotNull(
        "공식 굿즈 줄 수".takeIf { officialTotal != written.officialTotal },
        "공식 보유".takeIf { officialGotten != written.officialGotten },
        "공식 구매예정".takeIf { officialPending != written.officialPending },
        "2차창작".takeIf { fanTotal != written.fanTotal || fanGotten != written.fanGotten || fanPending != written.fanPending },
        "2차창작 시리즈".takeIf { fanSeries != written.fanSeries || fanSeriesNames != written.fanSeriesNames },
        "사진".takeIf { photoCount != written.photoCount || photoBytes != written.photoBytes },
        "선호 캐릭터".takeIf { favorites != written.favorites },
        "캐릭터별 개수".takeIf { byChara != written.byChara },
        "배신 굿즈".takeIf { betrayGenres != written.betrayGenres || betrayGoods != written.betrayGoods || betrayGotten != written.betrayGotten },
        "배신 굿즈 선호 캐릭터".takeIf { betrayDetail.favorites != written.betrayDetail.favorites },
        "배신 굿즈 캐릭터".takeIf { betrayDetail.charas != written.betrayDetail.charas },
        "배신 굿즈 시리즈".takeIf { betrayDetail.series != written.betrayDetail.series }
    )

    /** 불러와도 되살릴 것이 없는 백업 */
    val isEmpty: Boolean
        get() = officialGotten + officialPending == 0 && fanTotal == 0 && favorites.isEmpty() && betrayGenres == 0 && fanSeries == 0

    /** 굿즈 데이터를 아직 받지 않았거나 일부만 받은 것 같은지 */
    val goodsDataMissing: Boolean get() = officialTotal < MIN_GOODS_DATA

    companion object {
        /** 지금 데이터는 수천 줄이다. 이보다 적으면 업데이트가 제대로 되지 않은 것으로 본다. */
        const val MIN_GOODS_DATA = 100

        fun of(
            official: List<GoodsEntity>,
            fan: List<FanGoodsEntity>,
            charas: List<CharaEntity>,
            betray: List<BetrayGoodsEntity> = emptyList(),
            betrayGenres: Int = 0,
            /** 배신 굿즈 장르 배경·아이콘, 직접 추가한 캐릭터 사진 경로 */
            betrayGenreImages: List<String> = emptyList(),
            /** 2차창작 시리즈 사진 경로와 수 */
            fanSeriesImages: List<String> = emptyList(),
            fanSeriesCount: Int = 0,
            fanSeriesNames: List<String> = emptyList(),
            betrayDetail: BetrayDetail = BetrayDetail(),
            /** 사진 파일 크기. 파일이 없으면 null(백업에 들어가지 않는다) */
            photoSize: (File) -> Long? = { if (it.isFile) it.length() else null }
        ): ExportPreview {
            val ownedOfficial = official.filter { it.goodsCategory != CATEGORY_SET && it.status != GoodsStatus.NOT_GOTTEN }
            val ownedFan = fan.filter { it.status != GoodsStatus.NOT_GOTTEN }

            val counts = linkedMapOf<String, IntArray>() // 이름 → [보유, 구매예정]
            fun add(charas: String, status: GoodsStatus) {
                charas.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                    val c = counts.getOrPut(name) { IntArray(2) }
                    if (status == GoodsStatus.GOTTEN) c[0]++ else c[1]++
                }
            }
            ownedOfficial.forEach { add(it.goodsChara, it.status) }
            ownedFan.forEach { add(it.fanGoodsChara, it.status) }

            // 같은 이름의 사진은 백업에도 한 번만 들어간다(BackupZip.write 와 같이 2차창작 → 배신 굿즈 → 장르 사진 순, 있는 파일만).
            val photos = (fan.map { it.fanGoodsImgPath } + fanSeriesImages + betray.map { it.goodsImgPath } + betrayGenreImages)
                .filter { it.isNotEmpty() }
                .mapNotNull { path -> File(path).let { f -> photoSize(f)?.let { f.name to it } } }
                .distinctBy { it.first }
                .map { it.second }

            return ExportPreview(
                officialTotal = official.size,
                officialGotten = ownedOfficial.count { it.status == GoodsStatus.GOTTEN },
                officialPending = ownedOfficial.count { it.status == GoodsStatus.PENDING },
                fanTotal = fan.size,
                fanGotten = ownedFan.count { it.status == GoodsStatus.GOTTEN },
                fanPending = ownedFan.count { it.status == GoodsStatus.PENDING },
                photoCount = photos.size,
                photoBytes = photos.sum(),
                favorites = charas.filter { it.charaIsFavorite }
                    .sortedWith(compareBy<CharaEntity> { it.charaFavoriteOrder }.thenBy { it.charaId })
                    .map { it.charaNm },
                byChara = counts.map { (name, c) -> CharaCount(name, c[0], c[1]) }
                    .sortedWith(compareByDescending<CharaCount> { it.total }.thenByDescending { it.gotten }.thenBy { it.name }),
                betrayGenres = betrayGenres,
                betrayGoods = betray.size,
                betrayGotten = betray.count { it.status == GoodsStatus.GOTTEN },
                fanSeries = fanSeriesCount,
                fanSeriesNames = fanSeriesNames.sorted(),
                betrayDetail = betrayDetail
            ).apply {
                reviewGoods = ownedOfficial.map {
                    ReviewGoods(true, it.goodsSeries, it.goodsChara, it.goodsCategory, it.goodsPrice, it.goodsMemo, it.status, it.goodsQuantity, it.imgPath)
                } + fan.map {
                    ReviewGoods(false, it.fanGoodsSeries, it.fanGoodsChara, it.fanGoodsCategory, it.fanGoodsPrice, it.fanGoodsMemo, it.status, it.quantity, it.fanGoodsImgPath)
                }
                avatars = charas.associate { it.charaNm to it.charaUrl }
            }
        }

        /**
         * 만든 백업 zip 을 다시 열어 같은 기준으로 센다. 추출 뒤 [load] 결과와 같으면 파일이 제대로 써진 것이다.
         * 불러오기([BackupZip.restore])와 같은 방식으로 읽으므로, 여기서 센 것이 불러올 때 되살아나는 것이다.
         */
        fun fromZip(zipFile: File): ExportPreview {
            val gson = Gson()
            var officialJson: String? = null
            var fanJson: String? = null
            var favoriteJson: String? = null
            var betrayJson: String? = null
            var fanSeriesJson: String? = null
            val photoSizes = mutableMapOf<String, Long>() // 사진 이름 → 크기
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                    val name = parts.lastOrNull() ?: return@forEach
                    fun text() = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    when {
                        parts.size >= 2 && parts[parts.size - 2] == "images" -> photoSizes[name] = entry.size
                        name == "official_gotten.json" -> officialJson = text()
                        name == "fan_goods.json" -> fanJson = text()
                        name == "favorite_charas.json" -> favoriteJson = text()
                        name == "betray_goods.json" -> betrayJson = text()
                        name == BackupZip.FAN_SERIES -> fanSeriesJson = text()
                    }
                }
            }
            val official: List<OfficialGoodsBackup> = officialJson
                ?.let { gson.fromJson<List<OfficialGoodsBackup?>>(it, object : TypeToken<List<OfficialGoodsBackup?>>() {}.type) }
                .orEmpty().filterNotNull()
            val fan: List<FanGoodsBackup> = fanJson
                ?.let { gson.fromJson<List<FanGoodsBackup?>>(it, object : TypeToken<List<FanGoodsBackup?>>() {}.type) }
                .orEmpty().filterNotNull()
            val favorites: List<String> = favoriteJson
                ?.let { gson.fromJson<List<String?>>(it, object : TypeToken<List<String?>>() {}.type) }
                .orEmpty().filterNotNull()
            val betray = betrayJson?.let { gson.fromJson(it, BetrayBackup::class.java) }
            val fanSeries: List<FanSeriesBackup> = fanSeriesJson
                ?.let { gson.fromJson<List<FanSeriesBackup?>>(it, object : TypeToken<List<FanSeriesBackup?>>() {}.type) }
                .orEmpty().filterNotNull().filter { !it.seriesNm.isNullOrBlank() }.distinctBy { it.seriesNm!!.trim() }
            val betrayGenreIds = betray?.genres.orEmpty().filterNotNull().mapNotNull { it.genreId }.filter { it > 0 }.toSet()
            return of(
                official = official.map {
                    GoodsEntity(
                        goodsSeries = it.goodsSeries.orEmpty(),
                        goodsChara = it.goodsChara.orEmpty(),
                        goodsCategory = it.goodsCategory.orEmpty(),
                        goodsStatus = it.restoredStatus.name
                    )
                },
                fan = fan.map { it.toEntity(it.fanGoodsImgPath.orEmpty()) },
                // 선호 순서는 목록 순서 그대로다.
                charas = favorites.mapIndexed { i, n -> CharaEntity(charaId = i + 1, charaNm = n, charaIsFavorite = true, charaFavoriteOrder = i + 1) },
                // 불러오기와 같이 없는 장르를 가리키는 굿즈는 버린다.
                betray = betray?.goods.orEmpty().filterNotNull().filter { it.genreId in betrayGenreIds }
                    .map { it.toEntity(it.goodsImgPath.orEmpty()) },
                betrayGenres = betrayGenreIds.size,
                betrayGenreImages = betray?.genres.orEmpty().filterNotNull()
                    .filter { (it.genreId ?: 0) in betrayGenreIds }.flatMap { listOfNotNull(it.genreImgPath, it.genreIconPath) } +
                    betray?.charas.orEmpty().filterNotNull()
                        .filter { (it.genreId ?: 0) in betrayGenreIds && !it.charaNm.isNullOrBlank() && !it.charaNm.contains(',') }
                        .mapNotNull { it.charaImgPath } +
                    betray?.series.orEmpty().filterNotNull()
                        .filter { (it.genreId ?: 0) in betrayGenreIds && !it.seriesNm.isNullOrBlank() }
                        .flatMap { listOfNotNull(it.seriesImgPath, it.seriesIconPath) },
                fanSeriesImages = fanSeries.flatMap { listOfNotNull(it.seriesImgPath, it.seriesIconPath) },
                fanSeriesCount = fanSeries.size,
                fanSeriesNames = fanSeries.map { it.seriesNm!!.trim() },
                betrayDetail = run {
                    val names = betray?.genres.orEmpty().filterNotNull().filter { (it.genreId ?: 0) > 0 }.distinctBy { it.genreId }
                        .associate { it.genreId!! to it.genreNm.orEmpty() }
                    BetrayDetail.of(
                        genreName = names,
                        favorites = betray?.favorites.orEmpty().filterNotNull()
                            .filter { it.genreId in names && !it.charaNm.isNullOrBlank() }
                            .map { Triple(it.genreId!!, it.charaNm!!.trim(), it.favoriteOrder ?: 0) }
                            .distinctBy { it.first to it.second },
                        charas = betray?.charas.orEmpty().filterNotNull()
                            .filter { it.genreId in names && !it.charaNm.isNullOrBlank() && !it.charaNm.contains(',') }
                            .map { it.genreId!! to it.charaNm!!.trim() }.distinct(),
                        series = betray?.series.orEmpty().filterNotNull()
                            .filter { it.genreId in names && !it.seriesNm.isNullOrBlank() }
                            .map { it.genreId!! to it.seriesNm!!.trim() }.distinct()
                    )
                },
                photoSize = { photoSizes[it.name] }
            )
        }

        /** 지금 DB 에서 [selection] 범위만 센다(BackupZip.write 와 같은 범위). */
        suspend fun load(selection: ExportSelection = ExportSelection.ALL): ExportPreview = withContext(Dispatchers.IO) {
            val picked = BetrayPick.load(selection)
            val fanSeries = if (selection.includeWuwa) App.fanDatabase.fanGoodsDao().allSeries() else emptyList()
            of(
                official = if (selection.includeWuwa) App.database.goodsDao().getAll() else emptyList(),
                fan = if (selection.includeWuwa) App.fanDatabase.fanGoodsDao().getAll() else emptyList(),
                charas = if (selection.includeWuwa) App.charaDatabase.charaDao().getAll() else emptyList(),
                betray = picked?.goods.orEmpty(),
                betrayGenres = picked?.genres?.size ?: 0,
                betrayGenreImages = picked?.let { p ->
                    p.genres.flatMap { listOf(it.genreImgPath, it.genreIconPath) } + p.charas.map { it.charaImgPath } +
                        p.series.flatMap { listOf(it.seriesImgPath, it.seriesIconPath) }
                }.orEmpty(),
                fanSeriesImages = fanSeries.flatMap { listOf(it.seriesImgPath, it.seriesIconPath) },
                fanSeriesCount = fanSeries.size,
                fanSeriesNames = fanSeries.map { it.seriesNm },
                betrayDetail = picked?.let { p ->
                    BetrayDetail.of(
                        genreName = p.genres.associate { it.genreId to it.genreNm },
                        favorites = p.favorites.map { Triple(it.genreId, it.charaNm, it.favoriteOrder) },
                        charas = p.charas.map { it.genreId to it.charaNm },
                        series = p.series.map { it.genreId to it.seriesNm }
                    )
                } ?: BetrayDetail()
            ).apply {
                betrayReview = picked?.let { p -> betrayReviewOf(p.genres.map { it.genreId to it.genreNm }, p.goods, p.favorites, p.charas) }.orEmpty()
            }
        }

        /** 장르 순서대로, 굿즈가 있는 장르만 확인 목록을 만든다. 사진은 배신 굿즈 캐릭터 화면과 같은 기준 */
        internal fun betrayReviewOf(
            genres: List<Pair<Int, String>>,
            goods: List<BetrayGoodsEntity>,
            favorites: List<com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity>,
            charas: List<com.example.wowagoodsproject.db.betray.BetrayCharaEntity>
        ): List<BetrayReview> = genres.mapNotNull { (id, name) ->
            val inGenre = goods.filter { it.genreId == id }
            if (inGenre.isEmpty()) return@mapNotNull null
            val covers = com.example.wowagoodsproject.screen.betray.betrayCharas(
                inGenre, favorites.filter { it.genreId == id }, charas.filter { it.genreId == id }
            ).associate { it.name to it.cover }
            BetrayReview(
                genreId = id,
                genreName = name,
                goods = inGenre.map {
                    ReviewGoods(
                        it.kind == com.example.wowagoodsproject.db.betray.BetrayKind.OFFICIAL,
                        it.goodsSeries, it.goodsChara, it.goodsCategory, it.goodsPrice, it.goodsMemo, it.status, it.goodsQuantity, it.goodsImgPath
                    )
                },
                avatars = covers
            )
        }
    }
}
