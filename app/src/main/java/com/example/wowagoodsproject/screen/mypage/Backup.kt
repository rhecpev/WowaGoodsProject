package com.example.wowagoodsproject.screen.mypage

import androidx.room.withTransaction
import com.example.wowagoodsproject.db.betray.BetrayCharaEntity
import com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.betrayKindOf
import com.example.wowagoodsproject.db.betray.deleteUnusedImages
import com.example.wowagoodsproject.db.betray.usedImagePaths
import com.example.wowagoodsproject.db.betray.withStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.goodsStatusOf
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.deleteUnusedImages
import com.example.wowagoodsproject.db.fan.withStatus
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.withStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * 유저 데이터 백업(zip) 만들기와 불러오기.
 *
 * zip 안의 파일
 * - official_gotten.json: 공식 굿즈 한 줄씩([OfficialGoodsBackup])
 * - fan_goods.json: 2차창작 굿즈([FanGoodsBackup])
 * - favorite_charas.json: 선호 캐릭터 이름 목록 (2.1부터)
 * - betray_goods.json: 배신 굿즈(다른 장르) 장르·굿즈·선호 캐릭터([BetrayBackup], 3.1부터). 옛 앱·웹은 모르는 파일이라 건너뛴다.
 * - images/: 2차창작·배신 굿즈 사진
 *
 * 버전마다 공식 굿즈 줄의 칸이 다르다. 불러오기는 아래를 모두 받아야 한다.
 * - 1.0~2.0: 시리즈·캐릭터·카테고리·goodsIsGotten (+메모, 1.1~1.2는 메모 없음). 가격·상태 칸이 없다.
 * - 2.1: 상태·가격·구매일·구입처
 * - 2.2~2.5: 수령예정일·수량·배송 시작일
 * - 2.6: 굿즈 번호(goodsUid)
 */

/**
 * 백업 파일에 담기는 공식 굿즈 한 줄.
 *
 * 모든 칸이 nullable 인 이유: 예전 버전에서 만든 zip 에는 나중에 생긴 칸(상태·메모·구매 정보)이
 * 아예 없고, Gson 은 그 자리에 코틀린 기본값 대신 null 을 넣는다.
 * 그대로 엔티티에 옮기면 non-null 칸에 null 이 들어가 복원이 통째로 실패한다.
 */
data class OfficialGoodsBackup(
    val goodsSeries: String? = null,
    val goodsChara: String? = null,
    val goodsCategory: String? = null,
    val goodsStatus: String? = null,
    val goodsMemo: String? = null,
    val goodsPrice: String? = null,
    val goodsPurchaseDate: String? = null,
    val goodsPurchaseStore: String? = null,
    val goodsReceiveDate: String? = null,
    val goodsQuantity: Int? = null,
    val goodsShippingDate: String? = null,
    /** 원본 데이터의 굿즈 번호. 이 칸이 생기기 전 백업이거나 0 이면 내용으로 짝을 찾는다. */
    val goodsUid: Int? = null,
    /** 2.0까지 쓰던 보유 여부. 상태 칸이 없는 백업은 이것으로 보유를 되살린다. 옛 버전 앱이 읽을 수 있게 새 백업에도 적는다. */
    val goodsIsGotten: Boolean? = null
) {
    /** 되살릴 상태. 상태 칸이 없으면 예전 보유 여부로, 모르는 값이면 미보유로 본다. */
    val restoredStatus: GoodsStatus
        get() = goodsStatus?.let { goodsStatusOf(it) }
            ?: if (goodsIsGotten == true) GoodsStatus.GOTTEN else GoodsStatus.NOT_GOTTEN

    /** 보유·구매예정이라 되살릴 것이 있는 줄. 불러오기는 먼저 모든 굿즈를 미보유로 비우므로 나머지는 볼 필요가 없다. */
    val isOwned: Boolean get() = restoredStatus != GoodsStatus.NOT_GOTTEN

    fun applyTo(goods: GoodsEntity): GoodsEntity = goods.withStatus(restoredStatus.name).copy(
        goodsPurchaseDate = goodsPurchaseDate.orEmpty(),
        goodsPurchaseStore = goodsPurchaseStore.orEmpty(),
        goodsReceiveDate = goodsReceiveDate.orEmpty(),
        goodsQuantity = (goodsQuantity ?: 1).coerceAtLeast(1),
        goodsShippingDate = if (restoredStatus == GoodsStatus.PENDING) goodsShippingDate.orEmpty() else ""
    )

    companion object {
        fun of(goods: GoodsEntity) = OfficialGoodsBackup(
            goodsSeries = goods.goodsSeries,
            goodsChara = goods.goodsChara,
            goodsCategory = goods.goodsCategory,
            goodsStatus = goods.goodsStatus,
            goodsMemo = goods.goodsMemo,
            goodsPrice = goods.goodsPrice,
            goodsPurchaseDate = goods.goodsPurchaseDate,
            goodsPurchaseStore = goods.goodsPurchaseStore,
            goodsReceiveDate = goods.goodsReceiveDate,
            goodsQuantity = goods.goodsQuantity,
            goodsShippingDate = goods.goodsShippingDate,
            goodsUid = goods.goodsUid,
            goodsIsGotten = goods.goodsStatus == GoodsStatus.GOTTEN.name
        )
    }
}

/**
 * 백업 줄마다 되살릴 폰의 굿즈를 찾는다. 한 굿즈는 한 줄에만 짝지어진다.
 * 보유·구매예정 줄만 본다(미보유 줄이 느슨한 비교에서 다른 줄의 짝을 가로채지 않게).
 *
 * 1. 굿즈 번호가 같으면 같은 굿즈다.
 * 2. 시리즈·캐릭터·카테고리가 같고, 백업에 있는 칸(메모·가격)이 모두 같은 굿즈가 하나뿐이면 그 굿즈다.
 *    옛 백업은 메모·가격 칸이 없을 수 있다. 메모가 없는데 후보가 여럿이면 메모가 빈 굿즈 하나를 고른다.
 * 3. 백업 뒤에 데이터의 가격이나 메모가 바뀐 경우: 메모나 가격 중 하나라도 같은 굿즈가 하나뿐이면 그 굿즈다.
 *    둘 다 다르면 다른 굿즈일 수 있어 짝짓지 않는다.
 */
internal fun matchOfficialBackups(
    backups: List<OfficialGoodsBackup>,
    local: List<GoodsEntity>
): List<Pair<OfficialGoodsBackup, GoodsEntity>> {
    val owned = backups.filter { it.isOwned }
    val matched = linkedMapOf<Int, Pair<OfficialGoodsBackup, GoodsEntity>>() // 백업 줄 순번 → 짝
    val claimed = mutableSetOf<Int>() // 짝지어진 폰 굿즈 id

    fun claim(index: Int, goods: GoodsEntity) {
        matched[index] = owned[index] to goods
        claimed += goods.goodsId
    }

    val byUid = local.filter { it.goodsUid > 0 }.associateBy { it.goodsUid }
    owned.forEachIndexed { i, b ->
        val goods = (b.goodsUid ?: 0).takeIf { it > 0 }?.let { byUid[it] } ?: return@forEachIndexed
        if (goods.goodsId !in claimed) claim(i, goods)
    }

    val bySeriesKey = local.groupBy { Triple(it.goodsSeries, it.goodsChara, it.goodsCategory) }
    fun candidates(b: OfficialGoodsBackup) =
        bySeriesKey[Triple(b.goodsSeries.orEmpty(), b.goodsChara.orEmpty(), b.goodsCategory.orEmpty())]
            .orEmpty()
            .filter { it.goodsId !in claimed }

    owned.forEachIndexed { i, b ->
        if (i in matched) return@forEachIndexed
        var found = candidates(b).filter {
            (b.goodsMemo == null || it.goodsMemo == b.goodsMemo) && (b.goodsPrice == null || it.goodsPrice == b.goodsPrice)
        }
        if (found.size > 1 && b.goodsMemo == null) found = found.filter { it.goodsMemo.isEmpty() }
        found.singleOrNull()?.let { claim(i, it) }
    }

    owned.forEachIndexed { i, b ->
        if (i in matched || b.goodsMemo == null || b.goodsPrice == null) return@forEachIndexed
        val pool = candidates(b)
        (pool.filter { it.goodsMemo == b.goodsMemo }.singleOrNull()
            ?: pool.filter { it.goodsPrice == b.goodsPrice }.singleOrNull())
            ?.let { claim(i, it) }
    }

    return matched.values.toList()
}

/**
 * 불러올 때 굿즈 번호(PK)를 그대로 쓸 수 있는 가장 큰 값. 알림 번호가 '종류별 시작 번호 + PK' 라
 * 이보다 크면 다른 종류 알림과 번호가 겹친다(PendingGoodsNotifier 의 1_000_000 간격).
 */
internal const val MAX_KEPT_GOODS_ID = 999_999

/**
 * 백업에 적힌 굿즈 번호를 되살린다. '이 굿즈 알림 받지 않기' 기록이 번호로 남아 있어서, 번호가 바뀌면 풀린다.
 * 번호가 없거나(예전 백업), 범위를 넘거나, 파일 안에서 겹치거나, [taken](기기에 남아 있는 굿즈 번호)과 겹치면 0 으로 두어 새 번호를 받게 한다.
 * 번호를 지킨 줄을 먼저 두고 새 번호 줄을 뒤에 둔다. 새 번호가 먼저 들어가면 뒤에 오는 지킨 번호와 겹칠 수 있어서다.
 */
internal fun <T> withKeptIds(items: List<T>, taken: Set<Int>, idOf: (T) -> Int, setId: (T, Int) -> T): List<T> {
    val used = taken.toMutableSet()
    val (kept, fresh) = items.map { item ->
        val id = idOf(item)
        if (id in 1..MAX_KEPT_GOODS_ID && used.add(id)) item else setId(item, 0)
    }.partition { idOf(it) != 0 }
    return kept + fresh
}

/** 백업 파일에 담기는 2차창작 굿즈. nullable 인 이유는 [OfficialGoodsBackup] 과 같다. */
data class FanGoodsBackup(
    /** 굿즈 번호(PK). 앱은 처음부터 굿즈 줄을 통째로 적어 와서 들어 있지만, 없는 파일도 받는다. */
    val fanGoodsId: Int? = null,
    val fanGoodsReleaseDate: String? = null,
    val fanGoodsSeries: String? = null,
    /** 1.1~2.0 백업은 숫자(0 이면 안 적음)로 들어 있다. Gson 이 문자열로 읽는다. */
    val fanGoodsPrice: String? = null,
    val fanGoodsChara: String? = null,
    val fanGoodsCategory: String? = null,
    val fanGoodsImgPath: String? = null,
    val fanGoodsIsGotten: Boolean? = null,
    val fanGoodsStatus: String? = null,
    val fanGoodsMemo: String? = null,
    val fanGoodsPurchaseDate: String? = null,
    val fanGoodsPurchaseStore: String? = null,
    val fanGoodsReceiveDate: String? = null,
    val fanGoodsQuantity: Int? = null,
    val fanGoodsShippingDate: String? = null
) {
    /** @param imgPath 백업 안 이미지를 앱 폴더에 풀어 놓은 뒤의 경로 */
    fun toEntity(imgPath: String): FanGoodsEntity {
        // 상태 칸이 생기기 전 백업이면 보유 여부로 상태를 되살린다. 모르는 상태 값이면 미보유로 받는다.
        val status = fanGoodsStatus?.let { goodsStatusOf(it).name }
            ?: if (fanGoodsIsGotten == true) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name
        return FanGoodsEntity(
            // 그대로 써도 되는지는 불러올 때 withKeptIds 로 거른다.
            fanGoodsId = fanGoodsId ?: 0,
            fanGoodsReleaseDate = fanGoodsReleaseDate.orEmpty(),
            fanGoodsSeries = fanGoodsSeries.orEmpty(),
            // 옛 백업의 숫자 0 은 가격을 안 적은 것이다.
            fanGoodsPrice = fanGoodsPrice.orEmpty().let { if (it == "0") "" else it },
            fanGoodsChara = fanGoodsChara.orEmpty(),
            fanGoodsCategory = fanGoodsCategory.orEmpty(),
            fanGoodsImgPath = imgPath,
            fanGoodsMemo = fanGoodsMemo.orEmpty(),
            fanGoodsPurchaseDate = fanGoodsPurchaseDate.orEmpty(),
            fanGoodsPurchaseStore = fanGoodsPurchaseStore.orEmpty(),
            fanGoodsReceiveDate = fanGoodsReceiveDate.orEmpty(),
            fanGoodsQuantity = (fanGoodsQuantity ?: 1).coerceAtLeast(1),
            fanGoodsShippingDate = fanGoodsShippingDate.orEmpty()
        ).withStatus(status) // 예전 칸 fanGoodsIsGotten·수량·배송 시작일을 상태에 맞춘다
    }
}

/** 백업 파일의 배신 굿즈 묶음. 모든 칸이 nullable 인 이유는 [OfficialGoodsBackup] 과 같다. */
data class BetrayBackup(
    val genres: List<BetrayGenreBackup?>? = null,
    val goods: List<BetrayGoodsBackup?>? = null,
    val favorites: List<BetrayFavoriteBackup?>? = null,
    /** 직접 추가한 캐릭터(이름·사진). 이 칸이 생기기 전 배신 굿즈 백업에는 없다. */
    val charas: List<BetrayCharaBackup?>? = null,
    /**
     * true 면 일부 장르만 담은 백업(장르별 추출, 장르를 골라 추출). 불러오면 같은 이름 장르는 바꾸고 없는 장르는 더하며,
     * 파일에 없는 장르는 그대로 둔다. null/false 면 전체 백업이라 배신 굿즈 전체를 파일 것으로 바꾼다.
     */
    val partial: Boolean? = null,
    /** 등록한 시리즈 */
    val series: List<BetraySeriesBackup?>? = null
) {
    data class BetrayCharaBackup(val genreId: Int? = null, val charaNm: String? = null, val charaImgPath: String? = null)

    /** 등록한 시리즈(이름·배경·아이콘). 이 칸이 생기기 전 배신 굿즈 백업에는 없다. */
    data class BetraySeriesBackup(
        val genreId: Int? = null,
        val seriesNm: String? = null,
        val seriesImgPath: String? = null,
        val seriesIconPath: String? = null,
        /** 시리즈 날짜. 이 칸이 생기기 전 백업에는 없다(빈 값으로 불러온다) */
        val seriesDate: String? = null
    )

    /** [genreImgPath]·[genreIconPath] 는 장르 배경·아이콘 사진. 처음 배신 굿즈 백업에는 없다. */
    data class BetrayGenreBackup(
        val genreId: Int? = null,
        val genreNm: String? = null,
        val genreOrder: Int? = null,
        val genreImgPath: String? = null,
        val genreIconPath: String? = null
    )

    data class BetrayFavoriteBackup(val genreId: Int? = null, val charaNm: String? = null, val favoriteOrder: Int? = null)

    data class BetrayGoodsBackup(
        val genreId: Int? = null,
        val goodsKind: String? = null,
        val goodsRegDate: String? = null,
        val goodsSeries: String? = null,
        val goodsPrice: String? = null,
        val goodsChara: String? = null,
        val goodsCategory: String? = null,
        val goodsImgPath: String? = null,
        val goodsStatus: String? = null,
        val goodsMemo: String? = null,
        val goodsPurchaseDate: String? = null,
        val goodsPurchaseStore: String? = null,
        val goodsReceiveDate: String? = null,
        val goodsQuantity: Int? = null,
        val goodsShippingDate: String? = null,
        /** 굿즈 번호(PK). 3.1 의 이 칸이 생기기 전 배신 굿즈 백업에는 없다(새 번호를 받는다). */
        val goodsId: Int? = null
    ) {
        /** @param imgPath 백업 안 사진을 앱 폴더에 풀어 놓은 뒤의 경로 */
        fun toEntity(imgPath: String) = BetrayGoodsEntity(
            // 그대로 써도 되는지는 불러올 때 withKeptIds 로 거른다.
            goodsId = goodsId ?: 0,
            genreId = genreId ?: 0,
            goodsKind = betrayKindOf(goodsKind).name,
            goodsRegDate = goodsRegDate.orEmpty(),
            goodsSeries = goodsSeries.orEmpty(),
            goodsPrice = goodsPrice.orEmpty(),
            goodsChara = goodsChara.orEmpty(),
            goodsCategory = goodsCategory.orEmpty(),
            goodsImgPath = imgPath,
            goodsMemo = goodsMemo.orEmpty(),
            goodsPurchaseDate = goodsPurchaseDate.orEmpty(),
            goodsPurchaseStore = goodsPurchaseStore.orEmpty(),
            goodsReceiveDate = goodsReceiveDate.orEmpty(),
            goodsQuantity = (goodsQuantity ?: 1).coerceAtLeast(1),
            goodsShippingDate = goodsShippingDate.orEmpty()
        ).withStatus(goodsStatusOf(goodsStatus))
    }

    /** 장르가 하나라도 있으면 되살릴 것이 있다(굿즈가 없는 빈 장르도 사용자가 만든 것이다). */
    val hasData: Boolean get() = !genres.isNullOrEmpty()

    /**
     * 이름이 [name] 인 장르(앞뒤 공백·대소문자 무시)만 남긴 일부 백업. 파일에 그 장르가 없으면 null.
     * 이름이 같은 장르가 여럿이면 모두 남긴다(합치기 복원이 한 장르로 합친다). 다른 장르의 굿즈·선호·캐릭터·시리즈는 버린다.
     */
    fun onlyGenre(name: String): BetrayBackup? {
        val key = name.trim()
        val picked = genres.orEmpty().filterNotNull()
            .filter { (it.genreId ?: 0) > 0 && it.genreNm.orEmpty().trim().equals(key, ignoreCase = true) }
        if (picked.isEmpty()) return null
        val ids = picked.map { it.genreId }.toSet()
        return BetrayBackup(
            genres = picked,
            goods = goods.orEmpty().filter { it?.genreId in ids },
            favorites = favorites.orEmpty().filter { it?.genreId in ids },
            charas = charas.orEmpty().filter { it?.genreId in ids },
            partial = true,
            series = series.orEmpty().filter { it?.genreId in ids }
        )
    }

    companion object {
        fun of(
            genres: List<BetrayGenreEntity>,
            goods: List<BetrayGoodsEntity>,
            favorites: List<BetrayFavoriteEntity>,
            charas: List<BetrayCharaEntity> = emptyList(),
            series: List<com.example.wowagoodsproject.db.betray.BetraySeriesEntity> = emptyList()
        ) =
            BetrayBackup(
                genres = genres.map { BetrayGenreBackup(it.genreId, it.genreNm, it.genreOrder, it.genreImgPath, it.genreIconPath) },
                goods = goods.map {
                    BetrayGoodsBackup(
                        it.genreId, it.goodsKind, it.goodsRegDate, it.goodsSeries, it.goodsPrice, it.goodsChara,
                        it.goodsCategory, it.goodsImgPath, it.goodsStatus, it.goodsMemo, it.goodsPurchaseDate,
                        it.goodsPurchaseStore, it.goodsReceiveDate, it.goodsQuantity, it.goodsShippingDate, it.goodsId
                    )
                },
                favorites = favorites.map { BetrayFavoriteBackup(it.genreId, it.charaNm, it.favoriteOrder) },
                charas = charas.map { BetrayCharaBackup(it.genreId, it.charaNm, it.charaImgPath) },
                series = series.map { BetraySeriesBackup(it.genreId, it.seriesNm, it.seriesImgPath, it.seriesIconPath, it.seriesDate) }
            )
    }
}

/**
 * 백업에서 불러올 부분(불러오기 전 확인 창에서 고른 것).
 * @property betrayMerge true 면 배신 굿즈를 '합치기'로 불러온다(장르 복구): 파일에 있는 장르만 바꾸거나 더하고 다른 장르는 그대로.
 */
data class RestoreParts(
    val official: Boolean = true,
    val fan: Boolean = true,
    val favorites: Boolean = true,
    val fanSeries: Boolean = true,
    val betray: Boolean = true,
    val betrayMerge: Boolean = false,
    /** 장르 마이페이지의 데이터 입력: 파일에서 이 이름의 장르만 불러온다. 없으면 불러오지 않는다. */
    val onlyGenre: String? = null
) {
    companion object {
        val ALL = RestoreParts()
        /** 장르 마이페이지·장르 목록의 '복구': 배신 굿즈만, 합치기로 */
        val BETRAY_MERGE = RestoreParts(official = false, fan = false, favorites = false, fanSeries = false, betray = true, betrayMerge = true)
        fun of(selected: Set<ImportPart>, betrayMerge: Boolean = false) = RestoreParts(
            official = ImportPart.OFFICIAL in selected,
            fan = ImportPart.FAN in selected,
            favorites = ImportPart.FAVORITES in selected,
            fanSeries = ImportPart.FAN_SERIES in selected,
            betray = ImportPart.BETRAY in selected,
            betrayMerge = betrayMerge
        )
    }
}

/** 백업 파일의 2차창작 시리즈 한 줄 */
data class FanSeriesBackup(
    val seriesNm: String? = null,
    val seriesImgPath: String? = null,
    val seriesIconPath: String? = null,
    /** 시리즈 날짜. 이 칸이 생기기 전 백업에는 없다(빈 값으로 불러온다) */
    val seriesDate: String? = null
)

/**
 * 추출할 범위.
 * @property includeWuwa 명조(공식 보유 현황·2차창작·선호 캐릭터)를 담을지. 장르별 추출은 false
 * @property genreIds 담을 배신 굿즈 장르. null 이면 모든 장르(전체 백업), 비어 있으면 배신 굿즈를 담지 않는다.
 *   null 이 아니면 일부 장르 백업([BetrayBackup.partial])이 된다.
 */
data class ExportSelection(val includeWuwa: Boolean = true, val genreIds: Set<Int>? = null) {
    companion object {
        val ALL = ExportSelection()
        /** 배신 굿즈 장르 하나만(장르 마이페이지의 데이터 추출) */
        fun genreOnly(genreId: Int) = ExportSelection(includeWuwa = false, genreIds = setOf(genreId))
    }
}

/** [ExportSelection] 에 맞춰 고른 배신 굿즈. 백업 쓰기와 추출 확인이 같은 것을 센다. */
internal class BetrayPick(
    val genres: List<BetrayGenreEntity>,
    val goods: List<BetrayGoodsEntity>,
    val favorites: List<BetrayFavoriteEntity>,
    val charas: List<BetrayCharaEntity>,
    val series: List<com.example.wowagoodsproject.db.betray.BetraySeriesEntity> = emptyList()
) {
    /** 담길 사진(굿즈 → 장르 배경·아이콘 → 캐릭터 → 시리즈 순) */
    val photoPaths: List<String>
        get() = goods.map { it.goodsImgPath } + genres.flatMap { listOf(it.genreImgPath, it.genreIconPath) } + charas.map { it.charaImgPath } +
            series.flatMap { listOf(it.seriesImgPath, it.seriesIconPath) }

    companion object {
        /** 배신 굿즈를 담지 않으면 null */
        suspend fun load(selection: ExportSelection): BetrayPick? {
            val ids = selection.genreIds
            if (ids != null && ids.isEmpty()) return null
            val dao = App.betrayDatabase.betrayDao()
            fun keep(id: Int) = ids == null || id in ids
            val genres = dao.genres().filter { keep(it.genreId) }
            // 담을 장르가 없으면 배신 굿즈 파일을 넣지 않는다. 빈 '전체' 파일은 다른 기기에서 불러올 때 그 기기 장르를 모두 지운다.
            if (genres.isEmpty()) return null
            return BetrayPick(
                genres,
                dao.allGoods().filter { keep(it.genreId) },
                dao.allFavorites().filter { keep(it.genreId) },
                dao.allCharas().filter { keep(it.genreId) },
                dao.allSeries().filter { keep(it.genreId) }
            )
        }
    }
}

/** 불러오기 결과. 화면에 몇 개를 되살렸는지 알려 준다. */
data class RestoreReport(
    /** 백업에 있던 보유·구매예정 공식 굿즈 수. 백업에 공식 굿즈 파일이 없으면 null */
    val ownedInBackup: Int?,
    /** 그중 지금 데이터에서 찾아 되살린 수 */
    val restored: Int,
    /** 2차창작 굿즈 수. 백업에 2차창작이 없으면 null */
    val fanGoods: Int?,
    /** 선호 캐릭터 수. 백업에 선호 캐릭터가 없으면(2.0까지) null */
    val favorites: Int?,
    /** 배신 굿즈 수. 백업에 배신 굿즈 파일이 없으면 null */
    val betrayGoods: Int? = null,
    /** 불러온 배신 굿즈 장르 이름 */
    val betrayGenres: List<String>? = null,
    /** 2차창작(비공식) 시리즈 수. 백업에 시리즈 파일이 없거나 고르지 않았으면 null */
    val fanSeries: Int? = null,
    /** 불러오기 직전 / 직후 기기 상태. 결과 보고서의 Before / After 막대에 쓴다 */
    val before: ImportSnapshot? = null,
    val after: ImportSnapshot? = null
) {
    val missing: Int get() = (ownedInBackup ?: 0) - restored

    fun message(): String = listOfNotNull(
        ownedInBackup?.let { owned ->
            "보유·구매예정 ${owned}개 중 ${restored}개" + if (missing > 0) " (${missing}개는 지금 데이터에서 찾지 못함)" else ""
        },
        fanGoods?.let { "2차창작 ${it}개" },
        fanSeries?.let { "2차창작 시리즈 ${it}개" },
        favorites?.let { "선호 캐릭터 ${it}명" },
        betrayGoods?.let { "배신 굿즈 ${it}개" }
    ).joinToString(", ", prefix = "입력 완료! ")
}

object BackupZip {

    private const val OFFICIAL = "official_gotten.json"
    private const val FAN = "fan_goods.json"
    private const val FAVORITES = "favorite_charas.json"
    private const val BETRAY = "betray_goods.json"
    /** 2차창작 시리즈(이름·배경·아이콘). 이 파일이 생기기 전 백업에는 없다. */
    const val FAN_SERIES = "fan_series.json"
    private const val IMAGES = "images"
    /** 불러오는 동안 사진을 잠시 풀어 두는 폴더(앱 폴더 안) */
    private const val STAGING = ".restore_staging"

    /**
     * 백업 zip 을 [zipFile] 에 만든다.
     * [onProgress] 로 0~1 진행률을 알린다. DB 읽기·JSON 까지 0.2, 사진을 넣으며 0.2~1.
     */
    suspend fun write(
        zipFile: File,
        onProgress: (Float) -> Unit = {},
        /** 무엇을 담을지. 기본은 전체(명조 + 배신 굿즈 모든 장르) */
        selection: ExportSelection = ExportSelection.ALL
    ) = withContext(Dispatchers.IO) {
        onProgress(0f)
        val gson = Gson()
        // 기기에 사진 파일이 없으면 기록에도 사진을 적지 않는다. 기록과 zip 안 사진이 늘 맞아야,
        // 불러오는 쪽이 없는 사진을 가리키거나 멀쩡한 사진을 없는 사진으로 바꾸지 않는다.
        fun live(path: String) = if (path.isNotEmpty() && File(path).isFile) path else ""
        val fanGoods = (if (selection.includeWuwa) App.fanDatabase.fanGoodsDao().getAll() else emptyList())
            .map { it.copy(fanGoodsImgPath = live(it.fanGoodsImgPath)) }
        val officialGoods = if (selection.includeWuwa) App.database.goodsDao().getAll() else emptyList()
        // 이름 목록의 순서가 곧 선호 우선순위다(형식은 그대로 문자열 배열).
        val favoriteCharas = if (!selection.includeWuwa) emptyList() else App.charaDatabase.charaDao().getAll()
            .filter { it.charaIsFavorite }
            .sortedWith(compareBy<CharaEntity> { it.charaFavoriteOrder }.thenBy { it.charaId })
            .map { it.charaNm }
        val picked = BetrayPick.load(selection)?.let { p ->
            BetrayPick(
                p.genres.map { it.copy(genreImgPath = live(it.genreImgPath), genreIconPath = live(it.genreIconPath)) },
                p.goods.map { it.copy(goodsImgPath = live(it.goodsImgPath)) },
                p.favorites,
                p.charas.map { it.copy(charaImgPath = live(it.charaImgPath)) },
                p.series.map { it.copy(seriesImgPath = live(it.seriesImgPath), seriesIconPath = live(it.seriesIconPath)) }
            )
        }
        val betray = picked?.let {
            BetrayBackup.of(it.genres, it.goods, it.favorites, it.charas, it.series).copy(partial = if (selection.genreIds != null) true else null)
        }
        val fanSeries = (if (selection.includeWuwa) App.fanDatabase.fanGoodsDao().allSeries() else emptyList())
            .map { it.copy(seriesImgPath = live(it.seriesImgPath), seriesIconPath = live(it.seriesIconPath)) }
        val photoPaths = fanGoods.map { it.fanGoodsImgPath } + fanSeries.flatMap { listOf(it.seriesImgPath, it.seriesIconPath) } +
            picked?.photoPaths.orEmpty()

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            fun putText(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            // 명조를 빼고 추출하면(장르별 추출) 명조 파일을 아예 넣지 않는다. 불러올 때 명조 기록을 건드리지 않게.
            if (selection.includeWuwa) {
                putText(FAN, gson.toJson(fanGoods))
                putText(OFFICIAL, gson.toJson(officialGoods.map { OfficialGoodsBackup.of(it) }))
                putText(FAVORITES, gson.toJson(favoriteCharas))
                putText(FAN_SERIES, gson.toJson(fanSeries.map { FanSeriesBackup(it.seriesNm, it.seriesImgPath, it.seriesIconPath, it.seriesDate) }))
            }
            // 장르를 하나도 고르지 않았으면 배신 굿즈 파일도 넣지 않는다(불러올 때 배신 굿즈를 건드리지 않는다).
            if (betray != null) putText(BETRAY, gson.toJson(betray))
            onProgress(0.2f)
            // 같은 사진을 두 굿즈가 쓰면 같은 이름의 항목이 두 번 들어가 zip 만들기가 통째로 실패하므로 한 번만 넣는다.
            val written = mutableSetOf<String>()
            photoPaths.forEachIndexed { i, path ->
                onProgress(0.2f + 0.8f * i / photoPaths.size)
                if (path.isEmpty()) return@forEachIndexed
                val imgFile = File(path)
                if (!imgFile.isFile || !written.add(imgFile.name)) return@forEachIndexed
                zip.putNextEntry(ZipEntry("$IMAGES/${imgFile.name}"))
                FileInputStream(imgFile).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        onProgress(1f)
    }

    /**
     * [input] 의 백업을 불러온다. 사진은 [filesDir] 에 푼다.
     * JSON 을 모두 읽고 확인한 뒤에야 DB 를 바꾸므로, 백업이 깨져 있으면 아무것도 바뀌지 않는다.
     * 공식 굿즈·2차창작·선호 캐릭터는 각각 한 트랜잭션으로 바꾼다.
     */
    suspend fun restore(
        input: InputStream,
        filesDir: File,
        /** 불러올 부분. 기본은 파일에 든 것 전부 */
        parts: RestoreParts = RestoreParts.ALL,
        onProgress: (Float) -> Unit = {}
    ): RestoreReport = withContext(Dispatchers.IO) {
        // 사진은 먼저 임시 폴더에 풀고, 백업을 모두 확인한 뒤에 앱 폴더로 옮긴다.
        // 깨진 백업이라 멈추면 풀어 둔 사진도 남기지 않는다.
        val staging = File(filesDir, STAGING).apply { deleteRecursively(); mkdirs() }
        try {
            restoreWith(input, filesDir, staging, onProgress, parts)
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun restoreWith(
        input: InputStream,
        filesDir: File,
        staging: File,
        onProgress: (Float) -> Unit,
        parts: RestoreParts
    ): RestoreReport = withContext(Dispatchers.IO) {
        var fanJson: String? = null
        var officialJson: String? = null
        var favoriteJson: String? = null
        var betrayJson: String? = null
        var fanSeriesJson: String? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // PC 에서 풀었다 다시 묶어 "wowa_backup/fan_goods.json" 처럼 폴더가 하나 더 붙어도 알아본다.
                val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                val name = parts.lastOrNull().orEmpty()
                when {
                    entry.isDirectory || name.isEmpty() -> Unit
                    parts.size >= 2 && parts[parts.size - 2] == IMAGES -> {
                        // 경로는 버리고 파일 이름만 써서, zip 안의 "../" 같은 경로로 앱 폴더 밖에 쓰지 않게 한다.
                        // 사진을 메모리에 모아 두지 않고 바로 파일로 써서, 사진이 많아도 메모리가 넘치지 않는다.
                        if (name != "." && name != ".." && name != STAGING) {
                            File(staging, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    name == FAN -> fanJson = zip.bufferedReader().readText()
                    name == OFFICIAL -> officialJson = zip.bufferedReader().readText()
                    name == FAVORITES -> favoriteJson = zip.bufferedReader().readText()
                    name == BETRAY -> betrayJson = zip.bufferedReader().readText()
                    name == FAN_SERIES -> fanSeriesJson = zip.bufferedReader().readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (fanJson == null && officialJson == null && favoriteJson == null && betrayJson == null && fanSeriesJson == null) {
            throw IllegalStateException("백업 파일이 아닙니다")
        }
        onProgress(0.45f)

        // 바꾸기 전에 전부 읽어 둔다. 하나라도 깨져 있으면 여기서 멈춘다.
        // 빈 파일이나 "null" 은 빈 목록으로 보지 않는다. 공식 굿즈가 빈 목록이면 모든 보유 기록을 지우게 된다.
        val gson = Gson()
        // [null, {...}] 처럼 빈 항목이 섞여 있으면 그 항목만 버린다.
        fun <T : Any> parseList(name: String, json: String, type: java.lang.reflect.Type): List<T> =
            try {
                gson.fromJson<List<T?>?>(json, type)?.filterNotNull()
            } catch (e: Exception) {
                null
            } ?: throw IllegalStateException("백업 파일이 손상되었습니다 ($name)")
        val fanBackupsAll: List<FanGoodsBackup>? =
            fanJson?.let { parseList(FAN, it, object : TypeToken<List<FanGoodsBackup>>() {}.type) }
        val officialBackupsAll: List<OfficialGoodsBackup>? =
            officialJson?.let { parseList(OFFICIAL, it, object : TypeToken<List<OfficialGoodsBackup>>() {}.type) }
        val favoritesAll: List<String>? =
            favoriteJson?.let { parseList(FAVORITES, it, object : TypeToken<List<String>>() {}.type) }
        val fanSeriesBackupsAll: List<FanSeriesBackup>? =
            fanSeriesJson?.let { parseList(FAN_SERIES, it, object : TypeToken<List<FanSeriesBackup>>() {}.type) }
        val betrayAll: BetrayBackup? = betrayJson?.let {
            try {
                gson.fromJson(it, BetrayBackup::class.java)
            } catch (e: Exception) {
                null
            } ?: throw IllegalStateException("백업 파일이 손상되었습니다 ($BETRAY)")
        }
        // 고르지 않은 부분은 파일에 없는 것처럼 다룬다(지금 기록을 건드리지 않는다).
        val fanBackups = fanBackupsAll.takeIf { parts.fan }
        val officialBackups = officialBackupsAll.takeIf { parts.official }
        val favorites = favoritesAll.takeIf { parts.favorites }
        val fanSeriesBackups = fanSeriesBackupsAll.takeIf { parts.fanSeries }
        // 장르 복구처럼 '합치기'로 부르면 전체 백업이라도 파일에 있는 장르만 바꾸거나 더한다.
        val betray = betrayAll.takeIf { parts.betray }
            ?.let { if (parts.betrayMerge) it.copy(partial = true) else it }
            ?.let { all ->
                // 장르 마이페이지의 데이터 입력: 이름이 같은 장르만. 없으면 아무것도 바꾸지 않고 거부한다.
                val only = parts.onlyGenre ?: return@let all
                all.onlyGenre(only) ?: throw IllegalStateException("이 파일에는 '$only' 장르가 없어 불러오지 않았습니다")
            }
        if (parts.onlyGenre != null && betray == null) {
            throw IllegalStateException("이 파일에는 '${parts.onlyGenre}' 장르가 없어 불러오지 않았습니다")
        }

        // 굿즈 데이터를 아직 받지 않았으면 짝을 하나도 못 찾아 보유 기록이 모두 사라진다.
        if (officialBackups.orEmpty().any { it.isOwned } && App.database.goodsDao().count() == 0) {
            throw IllegalStateException("굿즈 데이터가 아직 없습니다. 마이페이지에서 수동 업데이트를 한 뒤 다시 불러오세요")
        }

        // 되살릴 것이 하나도 없는 백업(옛 버전에서 아무것도 보유하지 않은 채 만든 것 등)은 지금 기록을 지우기만 한다.
        // 지울 것이 있으면 불러오지 않는다. 백업에 들어 있는 부분만 바뀌므로 그 부분만 본다.
        val bringsNothing = officialBackups.orEmpty().none { it.isOwned } &&
            fanBackups.isNullOrEmpty() && favorites.isNullOrEmpty() && betray?.hasData != true &&
            fanSeriesBackups.isNullOrEmpty()
        if (bringsNothing) {
            val wouldErase =
                (officialBackups != null && App.database.goodsDao().getAll().any { it.goodsStatus != GoodsStatus.NOT_GOTTEN.name }) ||
                    (fanBackups != null && App.fanDatabase.fanGoodsDao().getAll().isNotEmpty()) ||
                    (fanSeriesBackups != null && App.fanDatabase.fanGoodsDao().allSeries().isNotEmpty()) ||
                    (favorites != null && App.charaDatabase.charaDao().getAll().any { it.charaIsFavorite }) ||
                    // 합치기(일부 장르) 백업은 파일에 있는 장르만 바꾸므로 지울 것이 없다.
                    (betray != null && betray.partial != true && App.betrayDatabase.betrayDao().genres().isNotEmpty())
            if (wouldErase) {
                throw IllegalStateException("백업에 보유·구매예정 굿즈, 2차창작·시리즈, 선호 캐릭터, 배신 굿즈가 하나도 없어 불러오지 않았습니다. 지금 기록은 그대로입니다")
            }
        }

        // 보고서용: 바꾸기 직전 상태
        val beforeSnapshot = runCatching { ImportSnapshot.take() }.getOrNull()

        // 확인을 마쳤으니 풀어 둔 사진을 앱 폴더로 옮긴다. 같은 이름이 있으면 백업 것으로 바꾼다.
        // 일부만 불러올 때는 고른 부분이 쓰는 사진만 옮긴다. 고르지 않은 부분의 사진이 같은 이름의
        // 기기 사진(그대로 두어야 할 기록이 쓰는 것)을 덮어쓰지 않게 한다.
        val wantedPhotos: Set<String>? = if (parts == RestoreParts.ALL) null else buildSet {
            fun addPath(path: String?) { path?.takeIf { it.isNotBlank() }?.let { add(File(it).name) } }
            fanBackups?.forEach { addPath(it.fanGoodsImgPath) }
            fanSeriesBackups?.forEach { addPath(it.seriesImgPath); addPath(it.seriesIconPath) }
            betray?.let { b ->
                b.genres.orEmpty().forEach { addPath(it?.genreImgPath); addPath(it?.genreIconPath) }
                b.goods.orEmpty().forEach { addPath(it?.goodsImgPath) }
                b.charas.orEmpty().forEach { addPath(it?.charaImgPath) }
                b.series.orEmpty().forEach { addPath(it?.seriesImgPath); addPath(it?.seriesIconPath) }
            }
        }
        val movedPhotos = mutableListOf<String>()
        staging.listFiles()?.filter { wantedPhotos == null || it.name in wantedPhotos }?.forEach { photo ->
            val target = File(filesDir, photo.name)
            if (!photo.renameTo(target)) photo.copyTo(target, overwrite = true)
            movedPhotos += photo.name
        }

        // 바뀌기 전 사진은 모든 부분을 다 쓴 뒤에 한 번에 지운다. 부분마다 지우면 뒤 부분(시리즈·배신 굿즈)이
        // 아직 들어가기 전이라, 뒤에서 쓸 사진을 '아무도 안 씀'으로 보고 지울 수 있다.
        val staleImages = mutableListOf<String>()
        fanBackups?.let { backups ->
            // 2차창작은 통째로 바꾸므로 파일 안에서만 겹치지 않으면 번호를 그대로 쓴다.
            val entities = withKeptIds(backups.map { backup ->
                val oldPath = backup.fanGoodsImgPath.orEmpty()
                val newPath = if (oldPath.isNotEmpty()) File(filesDir, File(oldPath).name).absolutePath else ""
                backup.toEntity(newPath)
            }, emptySet(), { it.fanGoodsId }, { g, id -> g.copy(fanGoodsId = id) })
            val dao = App.fanDatabase.fanGoodsDao()
            val oldImages = dao.getAll().map { it.fanGoodsImgPath }
            // 지우고 넣는 사이에 멈추면 2차창작 굿즈가 사라지므로 한 번에 처리한다.
            App.fanDatabase.withTransaction {
                dao.deleteAll()
                dao.insertAll(entities)
            }
            // 바뀌기 전 굿즈의 사진 중 이제 아무도 쓰지 않는 것은 지운다. 백업에서 푼 같은 이름의 사진은 새 굿즈가 쓰므로 남는다.
            staleImages += oldImages
        }
        var fanSeriesCount: Int? = null
        fanSeriesBackups?.let { backups ->
            // 파일이 있으면 2차창작 시리즈를 파일 것으로 바꾼다(없는 예전 백업이면 그대로 둔다).
            fun restored(path: String?) = path.orEmpty().let { p -> if (p.isNotEmpty()) File(filesDir, File(p).name).absolutePath else "" }
            val entities = backups.filter { !it.seriesNm.isNullOrBlank() }
                .map { com.example.wowagoodsproject.db.fan.FanSeriesEntity(it.seriesNm!!.trim(), restored(it.seriesImgPath), restored(it.seriesIconPath), it.seriesDate.orEmpty().trim()) }
                // 앱은 대소문자만 다른 시리즈 이름을 따로 두지 않는다(saveSeries 와 같은 기준).
                .distinctBy { it.seriesNm.lowercase() }
            val dao = App.fanDatabase.fanGoodsDao()
            val oldImages = dao.allSeries().flatMap { listOf(it.seriesImgPath, it.seriesIconPath) }
            App.fanDatabase.withTransaction {
                dao.deleteAllSeries()
                dao.insertAllSeries(entities)
            }
            staleImages += oldImages
            fanSeriesCount = entities.size
        }
        onProgress(0.6f)

        var ownedInBackup: Int? = null
        var restored = 0
        officialBackups?.let { backups ->
            // 상태를 비우고 다시 채우는 사이에 멈추면 보유 기록이 반만 남으므로 한 번에 처리한다.
            App.database.withTransaction {
                val dao = App.database.goodsDao()
                dao.resetAllGotten()
                val pairs = matchOfficialBackups(backups, dao.getAll())
                pairs.forEachIndexed { i, (backup, goods) ->
                    dao.update(backup.applyTo(goods))
                    onProgress(0.6f + 0.32f * (i + 1) / pairs.size)
                }
                ownedInBackup = backups.count { it.isOwned }
                restored = pairs.size
            }
        }
        onProgress(0.92f)

        favorites?.let { names ->
            // 목록 순서가 선호 우선순위다. 같은 이름이 두 번 있으면 앞의 것을 쓴다.
            val orderByName = names.map { it.trim() }.distinct().withIndex().associate { it.value to it.index + 1 }
            // 선호 표시가 반만 바뀐 채로 남지 않게 한 번에 처리한다.
            App.charaDatabase.withTransaction {
                val dao = App.charaDatabase.charaDao()
                dao.getAll().forEach { chara ->
                    val order = orderByName[chara.charaNm.trim()]
                    val favorite = order != null
                    val newOrder = order ?: 0
                    if (chara.charaIsFavorite != favorite || chara.charaFavoriteOrder != newOrder) {
                        dao.update(chara.copy(charaIsFavorite = favorite, charaFavoriteOrder = newOrder))
                    }
                }
            }
        }

        var betrayCount: Int? = null
        var betrayGenreNames: List<String>? = null
        betray?.let { backup ->
            // 장르 번호를 그대로 되살려 굿즈·선호 캐릭터가 같은 장르를 가리키게 한다. 없는 장르를 가리키는 줄은 버린다.
            val genres = backup.genres.orEmpty().filterNotNull()
                .filter { (it.genreId ?: 0) > 0 }.distinctBy { it.genreId }
                .map {
                    fun restored(path: String?) = path.orEmpty().let { p -> if (p.isNotEmpty()) File(filesDir, File(p).name).absolutePath else "" }
                    BetrayGenreEntity(it.genreId!!, it.genreNm.orEmpty(), it.genreOrder ?: 0, restored(it.genreImgPath), restored(it.genreIconPath))
                }
            val genreIds = genres.map { it.genreId }.toSet()
            val goods = backup.goods.orEmpty().filterNotNull().filter { it.genreId in genreIds }.map { g ->
                val oldPath = g.goodsImgPath.orEmpty()
                g.toEntity(if (oldPath.isNotEmpty()) File(filesDir, File(oldPath).name).absolutePath else "")
            }
            val favs = backup.favorites.orEmpty().filterNotNull()
                .filter { it.genreId in genreIds && !it.charaNm.isNullOrBlank() }
                .map { BetrayFavoriteEntity(it.genreId!!, it.charaNm!!.trim(), it.favoriteOrder ?: 0) }
                .distinctBy { it.genreId to it.charaNm }
            // 직접 추가한 캐릭터. 이 칸이 없는 예전 배신 굿즈 백업이면 비어 있다(전체 복원은 함께 비우고, 장르 합치기는 기기 것을 둔다).
            val charas = backup.charas.orEmpty().filterNotNull()
                .filter { it.genreId in genreIds && !it.charaNm.isNullOrBlank() && !it.charaNm.contains(',') }
                .map {
                    val p = it.charaImgPath.orEmpty()
                    BetrayCharaEntity(it.genreId!!, it.charaNm!!.trim(), if (p.isNotEmpty()) File(filesDir, File(p).name).absolutePath else "")
                }
                .distinctBy { it.genreId to it.charaNm }
            // 등록한 시리즈. 이 칸이 없는 예전 백업이면 비어 있다.
            val series = backup.series.orEmpty().filterNotNull()
                .filter { it.genreId in genreIds && !it.seriesNm.isNullOrBlank() }
                .map {
                    fun restored(path: String?) = path.orEmpty().let { p -> if (p.isNotEmpty()) File(filesDir, File(p).name).absolutePath else "" }
                    com.example.wowagoodsproject.db.betray.BetraySeriesEntity(it.genreId!!, it.seriesNm!!.trim(), restored(it.seriesImgPath), restored(it.seriesIconPath), it.seriesDate.orEmpty().trim())
                }
                .distinctBy { it.genreId to it.seriesNm }
            val dao = App.betrayDatabase.betrayDao()
            if (backup.partial == true) {
                // 일부 장르만 담은 백업: 같은 이름 장르는 내용을 바꾸고, 없는 장르는 새로 더한다. 다른 장르는 그대로.
                val oldImages = mutableListOf<String>()
                // 캐릭터·시리즈 칸이 없던 예전 백업(null)이면 기기의 캐릭터·시리즈를 그대로 둔다(배경·아이콘과 같은 기준).
                val keepCharas = backup.charas == null
                val keepSeries = backup.series == null
                App.betrayDatabase.withTransaction {
                    val local = dao.genres()
                    var nextOrder = local.maxOfOrNull { it.genreOrder } ?: 0
                    // 파일 안에 이름이 같은 장르가 둘 이상이면(대소문자·공백만 다름) 한 장르로 합친다.
                    // 앞 장르를 넣은 뒤 뒤 장르가 같은 기기 장르를 다시 비워 앞 굿즈가 사라지지 않게 한다.
                    val handled = mutableMapOf<String, Int>()
                    genres.forEach { g ->
                        val key = g.genreNm.trim().lowercase()
                        val same = local.firstOrNull { it.genreNm.trim().equals(g.genreNm.trim(), ignoreCase = true) }
                        val targetId = handled[key] ?: if (same != null) {
                            oldImages += dao.goodsOf(same.genreId).map { it.goodsImgPath } + listOf(same.genreImgPath, same.genreIconPath)
                            if (!keepCharas) oldImages += dao.charasOf(same.genreId).map { it.charaImgPath }
                            if (!keepSeries) oldImages += dao.seriesOf(same.genreId).flatMap { listOf(it.seriesImgPath, it.seriesIconPath) }
                            dao.deleteGoodsOf(same.genreId)
                            dao.deleteFavoritesOf(same.genreId)
                            if (!keepCharas) dao.deleteCharasOf(same.genreId)
                            if (!keepSeries) dao.deleteSeriesOf(same.genreId)
                            // 이 칸이 없던 예전 백업(null)이면 지금 배경·아이콘을 그대로 둔다. 빈 문자열이면 사진을 뺀 것이다.
                            val raw = backup.genres.orEmpty().firstOrNull { it?.genreId == g.genreId }
                            dao.updateGenre(
                                same.copy(
                                    genreImgPath = if (raw?.genreImgPath == null) same.genreImgPath else g.genreImgPath,
                                    genreIconPath = if (raw?.genreIconPath == null) same.genreIconPath else g.genreIconPath
                                )
                            )
                            same.genreId
                        } else {
                            nextOrder += 1
                            dao.insertGenre(g.copy(genreId = 0, genreOrder = nextOrder)).toInt()
                        }
                        handled[key] = targetId
                        // 기기에 남아 있는 다른 장르 굿즈와 번호가 겹치면 그 줄만 새 번호를 받는다.
                        val taken = dao.allGoods().map { it.goodsId }.toSet()
                        dao.insertAllGoods(
                            withKeptIds(goods.filter { it.genreId == g.genreId }.map { it.copy(genreId = targetId) }, taken,
                                { it.goodsId }, { x, id -> x.copy(goodsId = id) })
                        )
                        dao.insertAllFavorites(favs.filter { it.genreId == g.genreId }.map { it.copy(genreId = targetId) })
                        dao.insertAllCharas(charas.filter { it.genreId == g.genreId }.map { it.copy(genreId = targetId) })
                        dao.insertAllSeries(series.filter { it.genreId == g.genreId }.map { it.copy(genreId = targetId) })
                    }
                }
                staleImages += oldImages
            } else {
                val oldImages = dao.usedImagePaths()
                App.betrayDatabase.withTransaction {
                    dao.deleteAllGoods()
                    dao.deleteAllFavorites()
                    dao.deleteAllCharas()
                    dao.deleteAllSeries()
                    dao.deleteAllGenres()
                    dao.insertAllGenres(genres)
                    // 통째로 바꾸므로 파일 안에서만 겹치지 않으면 번호를 그대로 쓴다.
                    dao.insertAllGoods(withKeptIds(goods, emptySet(), { it.goodsId }, { x, id -> x.copy(goodsId = id) }))
                    dao.insertAllFavorites(favs)
                    dao.insertAllCharas(charas)
                    dao.insertAllSeries(series)
                }
                staleImages += oldImages
            }
            betrayCount = goods.size
            betrayGenreNames = genres.map { it.genreNm.trim() }
        }
        App.fanDatabase.fanGoodsDao().deleteUnusedImages(staleImages)
        // 일부 항목만 불러왔으면, 고르지 않은 부분의 사진처럼 옮겼지만 아무도 쓰지 않는 사진은 남기지 않는다.
        if (parts != RestoreParts.ALL) {
            val usedNames = com.example.wowagoodsproject.db.allUsedImagePaths().map { File(it).name }.toSet()
            movedPhotos.filter { it !in usedNames }.forEach { File(filesDir, it).delete() }
        }
        onProgress(1f)

        RestoreReport(
            ownedInBackup = ownedInBackup,
            restored = restored,
            fanGoods = fanBackups?.size,
            favorites = favorites?.size,
            betrayGoods = betrayCount,
            betrayGenres = betrayGenreNames,
            fanSeries = fanSeriesCount,
            before = beforeSnapshot,
            after = runCatching { ImportSnapshot.take() }.getOrNull()
        )
    }
}
