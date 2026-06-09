package com.example.wowagoodsproject.screen.fan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FanArtViewModel : ViewModel() {

    private val _goodsList = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val goodsList: StateFlow<List<FanGoodsEntity>> = _goodsList
    private val _allCharaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val allCharaList: StateFlow<List<CharaEntity>> = _allCharaList

    init {
        loadCharaMap()

    }

    fun loadCharaMap() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest { charaList ->
                _allCharaList.value = charaList
            }
        }
    }

    fun loadGoods() {
        viewModelScope.launch {
            _goodsList.value = App.fanDatabase.fanGoodsDao().getAll()
        }
    }

    fun toggleGotten(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val updated = goods.copy(fanGoodsIsGotten = !goods.fanGoodsIsGotten)
            App.fanDatabase.fanGoodsDao().update(updated)
            loadGoods()
        }
    }

    fun delete(goods: FanGoodsEntity) {
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().delete(goods)
            loadGoods()
        }
    }
}