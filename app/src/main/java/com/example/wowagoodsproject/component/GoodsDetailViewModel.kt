package com.example.wowagoodsproject.component

import androidx.lifecycle.ViewModel
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GoodsDetailViewModel : ViewModel() {

    private val _selectedGoods = MutableStateFlow<GoodsItem?>(null)
    val selectedGoods: StateFlow<GoodsItem?> = _selectedGoods

    fun selectGoods(goods: GoodsItem) {
        _selectedGoods.value = goods
    }

    /** 목록에서 굿즈를 눌렀을 때: 지금 상세에 떠 있는 굿즈를 다시 누르면 닫고, 아니면 연다 */
    fun toggleGoods(goods: GoodsItem) {
        _selectedGoods.value = if (isSameGoods(_selectedGoods.value, goods)) null else goods
    }

    fun dismissDialog() {
        _selectedGoods.value = null
    }
}

/** 굿즈 상세의 [GoodsDetailDialog] itemKey: 종류와 id 로 어떤 굿즈인지 */
fun goodsIdentity(g: GoodsItem): Any = when (g) {
    is GoodsEntity -> "o${g.goodsId}"
    is FanGoodsEntity -> "f${g.fanGoodsId}"
    is BetrayGoodsEntity -> "b${g.goodsId}"
    else -> g
}

/** 상태를 바꾸면 값이 달라지므로 내용 말고 종류·id 로 같은 굿즈인지 본다 */
internal fun isSameGoods(a: GoodsItem?, b: GoodsItem): Boolean = when {
    a is GoodsEntity && b is GoodsEntity -> a.goodsId == b.goodsId
    a is FanGoodsEntity && b is FanGoodsEntity -> a.fanGoodsId == b.fanGoodsId
    a is BetrayGoodsEntity && b is BetrayGoodsEntity -> a.goodsId == b.goodsId
    else -> false
}
