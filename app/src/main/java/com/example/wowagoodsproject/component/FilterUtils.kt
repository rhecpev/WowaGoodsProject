package com.example.wowagoodsproject.component

import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity

fun filterGoodsList(
    list: List<GoodsEntity>,
    allGoods: List<GoodsEntity>,
    charaFilter: String? = null,
    categoryFilter: String? = null
): List<GoodsEntity> {
    val setMemos = allGoods.filter { it.category == CATEGORY_SET }.map { it.memo }.toSet()

    var result = list.filter {
        it.category == CATEGORY_SET || it.memo !in setMemos || it.memo.isEmpty()
    }

    if (charaFilter != null) {
        result = result.filter { it.chara.contains(charaFilter) }
    }

    if (categoryFilter != null) {
        result = result.filter { goods ->
            if (goods.category == CATEGORY_SET) {
                allGoods.any {
                    it.category != CATEGORY_SET &&
                            it.memo == goods.memo &&
                            it.series == goods.series &&  // 추가
                            it.category == categoryFilter
                }
            } else {
                goods.category == categoryFilter
            }
        }
    }

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

fun filterGoodsListForBar(
    list: List<GoodsEntity>,
    charaFilter: String? = null,
    categoryFilter: String? = null
): List<GoodsEntity> {
    val setMemos = list.filter { it.category == CATEGORY_SET }.map { it.memo }.toSet()
    // 세트 제외, 구성품 포함
    var result = list.filter { it.category != CATEGORY_SET }
    if (charaFilter != null) result = result.filter { it.chara.contains(charaFilter) }
    if (categoryFilter != null) result = result.filter { it.category == categoryFilter }
    return result
}