package com.example.wowagoodsproject.component

import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity

/**
 * 구매예정 굿즈를 구매 정보로 거르는 필터. 여러 개를 고르면 모두 만족하는 굿즈만 남긴다.
 * [SHIPPING] 과 [NOT_SHIPPING] 은 서로 반대라 함께 고를 수 없다.
 */
enum class PendingInfoFilter(val label: String, val matches: (GoodsItem) -> Boolean) {
    NO_STORE("구입처 미정", { it.purchaseStore.isBlank() }),
    NO_PURCHASE_DATE("구매일 미정", { it.purchaseDate.isBlank() }),
    NO_RECEIVE_DATE("수령일 미정", { it.receiveDate.isBlank() }),
    SHIPPING("배송 중", { it.shippingDate.isNotBlank() }),
    NOT_SHIPPING("배송 전", { it.shippingDate.isBlank() });

    /** 함께 고를 수 없는 필터 */
    val opposite: PendingInfoFilter?
        get() = when (this) {
            SHIPPING -> NOT_SHIPPING
            NOT_SHIPPING -> SHIPPING
            else -> null
        }
}

/** [item] 을 켜고 끈다. 반대되는 필터가 켜져 있으면 끈다. */
fun Set<PendingInfoFilter>.toggle(item: PendingInfoFilter): Set<PendingInfoFilter> =
    if (item in this) this - item else this - setOfNotNull(item.opposite) + item

fun <T : GoodsItem> List<T>.filterPendingInfo(filters: Set<PendingInfoFilter>): List<T> =
    if (filters.isEmpty()) this else filter { goods -> filters.all { it.matches(goods) } }

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
    if (charaFilter != null) result = result.filter { it.chara.contains(charaFilter) }
    if (categoryFilter != null) result = result.filter { it.category == categoryFilter }
    return result
}

fun filterFanGoodsList(
    list: List<FanGoodsEntity>,
    charaFilter: String? = null,
    categoryFilter: String? = null
): List<FanGoodsEntity> {
    var result = list
    if (charaFilter != null) result = result.filter { it.chara.contains(charaFilter) }
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
