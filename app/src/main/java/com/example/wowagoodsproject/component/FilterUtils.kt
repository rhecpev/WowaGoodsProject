package com.example.wowagoodsproject.component

import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity

/**
 * 구매예정 굿즈를 구매 정보로 거르는 필터. 한 번에 하나만 고를 수 있다.
 * 이 순서대로 '전체' 뒤에 놓인다(배송 중 · 배송 전을 앞에).
 */
enum class PendingInfoFilter(val label: String, val matches: (GoodsItem) -> Boolean) {
    SHIPPING("배송 중", { it.shippingDate.isNotBlank() }),
    NOT_SHIPPING("배송 전", { it.shippingDate.isBlank() }),
    NO_STORE("구입처 미정", { it.purchaseStore.isBlank() }),
    NO_PURCHASE_DATE("구매일 미정", { it.purchaseDate.isBlank() }),
    NO_RECEIVE_DATE("수령일 미정", { it.receiveDate.isBlank() })
}

/** [filter] 가 null 이면 거르지 않는다(전체). */
fun <T : GoodsItem> List<T>.filterPendingInfo(filter: PendingInfoFilter?): List<T> =
    if (filter == null) this else filter(filter.matches)

/**
 * 캐릭터 칸("A,B")에 [name] 이 한 명으로 들어 있는지.
 * 글자 일부만 같은 다른 캐릭터는 아니다("양양현령" 굿즈는 "양양" 굿즈가 아니다).
 */
fun hasChara(charaField: String, name: String): Boolean {
    val target = name.trim()
    return target.isNotEmpty() && charaField.split(",").any { it.trim().equals(target, ignoreCase = true) }
}

/**
 * 목록용 필터.
 * 세트 굿즈는 목록에 노출하지 않고, 세트에 묶인 구성품을 포함한 모든 낱개 굿즈를 그대로 보여준다.
 */
fun filterGoodsList(
    list: List<GoodsEntity>,
    charaFilter: String? = null,
    categoryFilter: String? = null,
    seriesFilter: String? = null
): List<GoodsEntity> {
    var result = list.filter { it.category != CATEGORY_SET }
    if (charaFilter != null) result = result.filter { hasChara(it.chara, charaFilter) }
    if (categoryFilter != null) result = result.filter { it.category == categoryFilter }
    if (seriesFilter != null) result = result.filter { it.series.trim() == seriesFilter }
    return result
}

fun filterFanGoodsList(
    list: List<FanGoodsEntity>,
    charaFilter: String? = null,
    categoryFilter: String? = null,
    seriesFilter: String? = null
): List<FanGoodsEntity> {
    var result = list
    if (charaFilter != null) result = result.filter { hasChara(it.chara, charaFilter) }
    if (categoryFilter != null) result = result.filter { it.category == categoryFilter }
    if (seriesFilter != null) result = result.filter { it.series.trim() == seriesFilter }
    return result
}

/** 필터 팝업의 시리즈 탭에 보여 줄 값. 시리즈를 비워 둔 굿즈(2차창작 등)는 고를 거리가 없어 뺀다. */
fun seriesOptions(list: List<GoodsItem>): List<String> =
    list.map { it.series.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

/** 낱개 굿즈가 속한 세트 굿즈를 찾는다. 세트에 속하지 않으면 null. */
fun findSetGoods(goods: GoodsEntity, allGoods: List<GoodsEntity>): GoodsEntity? {
    if (goods.category == CATEGORY_SET || goods.memo.isEmpty()) return null
    return allGoods.find {
        it.category == CATEGORY_SET && it.memo == goods.memo && it.series == goods.series
    }
}

/** 세트 굿즈에 묶인 구성품 목록. */
fun getSetComponents(setGoods: GoodsEntity, allGoods: List<GoodsEntity>): List<GoodsEntity> =
    allGoods.filter {
        it.category != CATEGORY_SET && it.memo == setGoods.memo && it.series == setGoods.series
    }

/**
 * 필터 그리드 칸(카테고리·시리즈 등)마다 쓸 대표 사진 경로.
 * [keyOf] 가 그 칸 값과 같은 굿즈 중 선호 1순위 캐릭터의 굿즈 사진을 쓰고, 없으면 2순위, 3순위… 순으로 찾는다.
 * 선호 캐릭터 굿즈가 하나도 없으면 그 칸의 아무 굿즈 사진을 쓴다. 사진이 있는 굿즈가 없으면 결과에 넣지 않는다.
 * [goods] 의 앞쪽 굿즈를 먼저 고르므로, 공식 굿즈를 2차창작보다 앞에 두면 공식 사진이 우선한다. 세트 굿즈는 쓰지 않는다.
 * @param favoritesInOrder 선호 캐릭터 이름. 우선순위 순서(1순위가 맨 앞).
 */
fun coverImages(
    keys: List<String>,
    goods: List<GoodsItem>,
    favoritesInOrder: List<String>,
    keyOf: (GoodsItem) -> String
): Map<String, String> {
    val withImage = goods.filter { it.imgPath.isNotBlank() && it.category != CATEGORY_SET }
        .groupBy(keyOf)
    return keys.mapNotNull { key ->
        val candidates = withImage[key].orEmpty()
        val pick = favoritesInOrder.firstNotNullOfOrNull { fav -> candidates.firstOrNull { hasChara(it.chara, fav) } }
            ?: candidates.firstOrNull()
        pick?.let { key to it.imgPath }
    }.toMap()
}

/** 카테고리별 대표 사진. 규칙은 [coverImages]. */
fun categoryCoverImages(
    categories: List<String>,
    goods: List<GoodsItem>,
    favoritesInOrder: List<String>
): Map<String, String> = coverImages(categories, goods, favoritesInOrder) { it.category }

/** 시리즈별 대표 사진. 시리즈 DB에 그림이 없는 시리즈(2차창작에 직접 적은 시리즈 등)에 쓴다. 규칙은 [coverImages]. */
fun seriesCoverImages(
    series: List<String>,
    goods: List<GoodsItem>,
    favoritesInOrder: List<String>
): Map<String, String> = coverImages(series, goods, favoritesInOrder) { it.series.trim() }
