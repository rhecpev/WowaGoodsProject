package com.example.wowagoodsproject.screen.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CharacterViewModel : ViewModel() {

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _selectedChara = MutableStateFlow<CharaEntity?>(null)
    val selectedChara: StateFlow<CharaEntity?> = _selectedChara

    private val _officialGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val officialGoods: StateFlow<List<GoodsEntity>> = _officialGoods

    private val _fanGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGoods: StateFlow<List<FanGoodsEntity>> = _fanGoods

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab
    private val _showFavoriteOnly = MutableStateFlow(false)
    val showFavoriteOnly: StateFlow<Boolean> = _showFavoriteOnly

    fun toggleFavoriteOnly() {
        _showFavoriteOnly.value = !_showFavoriteOnly.value
    }

    init {
        loadCharaList()
    }

    fun loadCharaList() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest {
                _charaList.value = it
            }
        }
    }

    fun selectChara(chara: CharaEntity) {
        _selectedChara.value = chara
        viewModelScope.launch {
            App.database.goodsDao().getByCharaFlow(chara.charaNm).collectLatest {
                _officialGoods.value = it
            }
        }
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().getByCharaFlow(chara.charaNm).collectLatest {
                _fanGoods.value = it
            }
        }
    }

    fun clearSelectedChara() {
        _selectedChara.value = null
        _officialGoods.value = emptyList()
        _fanGoods.value = emptyList()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch {
            val updated = goods.copy(goodsIsGotten = !goods.goodsIsGotten)
            App.database.goodsDao().update(updated)
        }
    }

    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val updated = goods.copy(fanGoodsIsGotten = !goods.fanGoodsIsGotten)
            App.fanDatabase.fanGoodsDao().update(updated)
        }
    }

    fun deleteFanGoods(goods: FanGoodsEntity) {
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().delete(goods)
        }
    }



}