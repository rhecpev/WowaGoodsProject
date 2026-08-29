package com.example.wowagoodsproject.component

import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity

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
