package com.example.wowagoodsproject.screen.betray

import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.betray.BetrayCharaEntity
import com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.db.betray.charaNames
import com.example.wowagoodsproject.db.character.CharaEntity

/*
 * 배신 굿즈 화면들이 쓰는 계산. 화면과 떼어 두어 테스트할 수 있게 한다.
 */

/** 시리즈를 비워 둔 굿즈를 묶는 이름 */
const val NO_SERIES = "미분류"

/** 굿즈 탭의 시리즈 한 칸 */
data class BetraySeries(
    val name: String,
    val total: Int,
    val gotten: Int,
    val pending: Int,
    /** 대표 사진. 선호 캐릭터 굿즈 사진을 먼저 쓴다. 없으면 빈 문자열. */
    val cover: String,
    /** 가장 최근에 등록한 날. 시리즈 목록은 최근 등록 순이다. */
    val latest: String
)

/** 캐릭터별 탭의 캐릭터 한 명 */
data class BetrayChara(
    val name: String,
    val total: Int,
    val gotten: Int,
    val favorite: Boolean,
    /** 선호 우선순위(선호가 아니면 0) */
    val favoriteOrder: Int,
    val cover: String,
    /** 사용자가 직접 추가한 캐릭터인지(이름·사진을 고칠 수 있다) */
    val registered: Boolean = false
)

/**
 * 알림을 눌러 열 배신 굿즈. 장르 목록 → 그 장르의 그 시리즈 → 상세 순으로 화면이 차례로 받아 열고, 상세를 띄우면 비운다.
 */
/** 배신 굿즈 수정 요청. 굿즈 상세의 '이 굿즈 수정'이 넣고, 장르 화면이 받아 수정 화면을 연 뒤 비운다. */
object BetrayEditRequest {
    val goods = kotlinx.coroutines.flow.MutableStateFlow<BetrayGoodsEntity?>(null)
}

object BetrayOpenRequest {
    val goods = kotlinx.coroutines.flow.MutableStateFlow<BetrayGoodsEntity?>(null)
}

fun seriesKey(goods: BetrayGoodsEntity): String = goods.goodsSeries.trim().ifEmpty { NO_SERIES }

/** 굿즈를 시리즈로 묶는다. [kind] 가 null 이면 공식·2차창작을 함께 묶는다. 최근 등록한 시리즈가 위다. */
fun betraySeries(goods: List<BetrayGoodsEntity>, kind: BetrayKind?, favorites: List<String>): List<BetraySeries> =
    goods.filter { kind == null || it.kind == kind }
        .groupBy { seriesKey(it) }
        .map { (name, list) ->
            BetraySeries(
                name = name,
                total = list.size,
                gotten = list.count { it.status == GoodsStatus.GOTTEN },
                pending = list.count { it.status == GoodsStatus.PENDING },
                cover = coverOf(list, favorites),
                latest = list.maxOf { it.goodsRegDate }
            )
        }
        .sortedWith(compareByDescending<BetraySeries> { it.latest }.thenBy { it.name })

/**
 * 캐릭터들: 굿즈에 나온 캐릭터 + 직접 추가한 캐릭터([registered], 굿즈가 없어도 나온다).
 * 선호 캐릭터(우선순위 순)가 먼저, 그다음 굿즈가 많은 순.
 */
fun betrayCharas(
    goods: List<BetrayGoodsEntity>,
    favorites: List<BetrayFavoriteEntity>,
    registered: List<BetrayCharaEntity> = emptyList()
): List<BetrayChara> {
    val order = favorites.withIndex().associate { (i, f) -> f.charaNm to i + 1 }
    val favoriteNames = favorites.map { it.charaNm }
    val byName = linkedMapOf<String, MutableList<BetrayGoodsEntity>>()
    goods.forEach { g -> charaNames(g.goodsChara).forEach { byName.getOrPut(it) { mutableListOf() } += g } }
    registered.forEach { byName.getOrPut(it.charaNm) { mutableListOf() } }
    val photos = registered.associate { it.charaNm to it.charaImgPath }
    return byName.map { (name, list) ->
        BetrayChara(
            name = name,
            total = list.size,
            gotten = list.count { it.status == GoodsStatus.GOTTEN },
            favorite = name in order,
            favoriteOrder = order[name] ?: 0,
            // 직접 넣은 사진이 먼저, 없으면 그 캐릭터 혼자 나온 굿즈 사진.
            cover = photos[name].orEmpty().ifEmpty { coverOf(list.sortedBy { charaNames(it.goodsChara).size }, favoriteNames) },
            registered = name in photos
        )
    }.sortedWith(
        compareByDescending<BetrayChara> { it.favorite }
            .thenBy { if (it.favorite) it.favoriteOrder else 0 }
            .thenByDescending { it.total }
            .thenBy { it.name }
    )
}

/** 필터 팝업(GoodsFilterDialog)이 받는 캐릭터 목록. 캐릭터 DB 가 없으므로 그 자리에서 만든다. */
fun BetrayChara.asCharaEntity(id: Int) =
    CharaEntity(charaId = id, charaNm = name, charaUrl = cover, charaIsFavorite = favorite, charaFavoriteOrder = favoriteOrder)

private fun coverOf(list: List<BetrayGoodsEntity>, favorites: List<String>): String {
    val withImage = list.filter { it.goodsImgPath.isNotEmpty() }
    return (favorites.firstNotNullOfOrNull { fav -> withImage.firstOrNull { fav in charaNames(it.goodsChara) } }
        ?: withImage.firstOrNull())?.goodsImgPath.orEmpty()
}

/**
 * 검색. 띄어쓰기로 나눈 낱말이 모두 시리즈·캐릭터·카테고리·가격·메모 중 어딘가에 들어 있는 굿즈.
 * 빈 검색어면 아무것도 돌려주지 않는다(검색 전 화면).
 */
fun searchBetray(goods: List<BetrayGoodsEntity>, query: String, kind: BetrayKind? = null): List<BetrayGoodsEntity> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return goods.filter { g ->
        (kind == null || g.kind == kind) && words.all { w ->
            listOf(g.goodsSeries, g.goodsChara, g.goodsCategory, g.goodsPrice, g.goodsMemo).any { it.contains(w, ignoreCase = true) }
        }
    }
}

/** 목록은 시리즈끼리 붙어 있어야 시리즈 경계선이 한 번씩만 그어진다. 시리즈 안에서는 최근 등록이 위다. */
fun sortForList(goods: List<BetrayGoodsEntity>): List<BetrayGoodsEntity> {
    val seriesLatest = goods.groupBy { seriesKey(it) }.mapValues { (_, l) -> l.maxOf { it.goodsRegDate } }
    return goods.sortedWith(
        compareByDescending<BetrayGoodsEntity> { seriesLatest[seriesKey(it)] }
            .thenBy { seriesKey(it) }
            .thenByDescending { it.goodsRegDate }
            .thenByDescending { it.goodsId }
    )
}

/** 캐릭터마다 (공식, 2차창작) 굿즈 수. 캐릭터 칸("A,B")의 캐릭터마다 1씩 센다(게이지 범례와 같은 기준). */
fun charaKindBreakdown(goods: List<BetrayGoodsEntity>): Map<String, Pair<Int, Int>> {
    val map = mutableMapOf<String, Pair<Int, Int>>()
    goods.forEach { g ->
        charaNames(g.goodsChara).forEach { name ->
            val (o, f) = map[name] ?: (0 to 0)
            map[name] = if (g.kind == BetrayKind.OFFICIAL) (o + 1) to f else o to (f + 1)
        }
    }
    return map
}
