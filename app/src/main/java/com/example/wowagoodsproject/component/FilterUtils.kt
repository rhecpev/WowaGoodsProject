package com.example.wowagoodsproject.component

import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity

/**
 * 구매예정 굿즈를 구매 정보로 거르는 필터. 한 번에 하나만 고를 수 있다.
 */
enum class PendingInfoFilter(val label: String, val matches: (GoodsItem) -> Boolean) {
    NO_STORE("구입처 미정", { it.purchaseStore.isBlank() }),
    NO_PURCHASE_DATE("구매일 미정", { it.purchaseDate.isBlank() }),
    NO_RECEIVE_DATE("수령일 미정", { it.receiveDate.isBlank() }),
    SHIPPING("배송 중", { it.shippingDate.isNotBlank() }),
    NOT_SHIPPING("배송 전", { it.shippingDate.isBlank() })
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
    categoryFilter: String? = null
): List<GoodsEntity> {
    var result = list.filter { it.category != CATEGORY_SET }
    if (charaFilter != null) result = result.filter { hasChara(it.chara, charaFilter) }
    if (categoryFilter != null) result = result.filter { it.category == categoryFilter }
    return result
}

fun filterFanGoodsList(
    list: List<FanGoodsEntity>,
    charaFilter: String? = null,
    categoryFilter: String? = null
): List<FanGoodsEntity> {
    var result = list
    if (charaFilter != null) result = result.filter { hasChara(it.chara, charaFilter) }
    if (categoryFilter != null) result = result.filter { it.category == categoryFilter }
    return result
}

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
