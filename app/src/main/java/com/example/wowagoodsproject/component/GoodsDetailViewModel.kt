package com.example.wowagoodsproject.component

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GoodsDetailViewModel : ViewModel() {

    private val _selectedGoods = MutableStateFlow<GoodsItem?>(null)
    val selectedGoods: StateFlow<GoodsItem?> = _selectedGoods

    fun selectGoods(goods: GoodsItem) {
        _selectedGoods.value = goods
    }

    fun dismissDialog() {
        _selectedGoods.value = null
    }
}