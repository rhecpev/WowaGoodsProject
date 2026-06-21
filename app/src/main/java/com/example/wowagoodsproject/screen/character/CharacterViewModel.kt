package com.example.wowagoodsproject.screen.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
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

    private val _allSeriesGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val allSeriesGoods: StateFlow<List<GoodsEntity>> = _allSeriesGoods

    private val _fanGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGoods: StateFlow<List<FanGoodsEntity>> = _fanGoods

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _showFavoriteOnly = MutableStateFlow(false)

    val showFavoriteOnly: StateFlow<Boolean> = _showFavoriteOnly

    private val _charaGoodsCountMap = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val charaGoodsCountMap: StateFlow<Map<String, Pair<Int, Int>>> = _charaGoodsCountMap
    fun toggleFavoriteOnly() {
        _showFavoriteOnly.value = !_showFavoriteOnly.value
    }

    init {
        loadCharaList()
        loadCharaGoodsCount()
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
            val charaGoods = App.database.goodsDao().getByChara(chara.charaNm)
            _officialGoods.value = charaGoods
            val seriesList = charaGoods.map { it.goodsSeries }.distinct()
            _allSeriesGoods.value = seriesList.flatMap {
                App.database.goodsDao().getBySeries(it)
            }
            _fanGoods.value = App.fanDatabase.fanGoodsDao().getByChara(chara.charaNm)
        }
    }
    fun clearSelectedChara() {
        _selectedChara.value = null
        _officialGoods.value = emptyList()
        _allSeriesGoods.value = emptyList()
        _fanGoods.value = emptyList()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN
            val updated = goods.copy(goodsStatus = newStatus.name)
            App.database.goodsDao().update(updated)

            if (updated.goodsCategory != CATEGORY_SET && updated.goodsMemo.isNotEmpty()) {
                val allGoods = App.database.goodsDao().getBySeries(updated.goodsSeries)
                val siblings = allGoods.filter {
                    it.goodsCategory != CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                val setGoods = allGoods.find {
                    it.goodsCategory == CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                setGoods?.let { set ->
                    val newIsGotten = siblings.all { it.goodsStatus == GoodsStatus.GOTTEN.name }
                    App.database.goodsDao().update(set.copy(goodsStatus = if (newIsGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name))
                }
            }

            _selectedChara.value?.let { chara ->
                val charaGoods = App.database.goodsDao().getByChara(chara.charaNm)
                _officialGoods.value = charaGoods
                val seriesList = charaGoods.map { it.goodsSeries }.distinct()
                _allSeriesGoods.value = seriesList.flatMap {
                    App.database.goodsDao().getBySeries(it)
                }
            }
        }
    }

    fun setOfficialPending(goods: GoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING
            val updated = goods.copy(goodsStatus = newStatus.name)
            App.database.goodsDao().update(updated)

            if (updated.goodsCategory != CATEGORY_SET && updated.goodsMemo.isNotEmpty()) {
                val allGoods = App.database.goodsDao().getBySeries(updated.goodsSeries)
                val siblings = allGoods.filter {
                    it.goodsCategory != CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                val setGoods = allGoods.find {
                    it.goodsCategory == CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                setGoods?.let { set ->
                    val newIsGotten = siblings.all { it.goodsStatus == GoodsStatus.GOTTEN.name }
                    App.database.goodsDao().update(set.copy(goodsStatus = if (newIsGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name))
                }
            }

            _selectedChara.value?.let { chara ->
                val charaGoods = App.database.goodsDao().getByChara(chara.charaNm)
                _officialGoods.value = charaGoods
                val seriesList = charaGoods.map { it.goodsSeries }.distinct()
                _allSeriesGoods.value = seriesList.flatMap {
                    App.database.goodsDao().getBySeries(it)
                }
            }
        }
    }

    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN
            val updated = goods.copy(fanGoodsStatus = newStatus.name)
            App.fanDatabase.fanGoodsDao().update(updated)
        }
    }

    fun setFanPending(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING
            val updated = goods.copy(fanGoodsStatus = newStatus.name)
            App.fanDatabase.fanGoodsDao().update(updated)
        }
    }
    // After - 함수 추가 (setOfficialPending 아래)
    fun bulkToggleOfficialGotten(setGoods: GoodsEntity, isGotten: Boolean) {
        viewModelScope.launch {
            val allGoods = App.database.goodsDao().getBySeries(setGoods.goodsSeries)
            val components = allGoods.filter {
                it.goodsCategory != CATEGORY_SET && it.goodsMemo == setGoods.goodsMemo
            }
            val newStatus = if (isGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name
            components.forEach { comp ->
                App.database.goodsDao().update(comp.copy(goodsStatus = newStatus))
            }
            App.database.goodsDao().update(setGoods.copy(goodsStatus = newStatus))
            _selectedChara.value?.let { chara ->
                val charaGoods = App.database.goodsDao().getByChara(chara.charaNm)
                _officialGoods.value = charaGoods
                val seriesList = charaGoods.map { it.goodsSeries }.distinct()
                _allSeriesGoods.value = seriesList.flatMap {
                    App.database.goodsDao().getBySeries(it)
                }
            }
        }
    }
    // After - loadCharaList() 아래에 함수 추가
    private fun loadCharaGoodsCount() {
        viewModelScope.launch {
            App.database.goodsDao().getAllFlow().collectLatest { allGoods ->
                val countMap = mutableMapOf<String, Pair<Int, Int>>()
                allGoods.filter { it.goodsCategory != CATEGORY_SET }.forEach { goods ->
                    goods.goodsChara.split(",").forEach { chara ->
                        val name = chara.trim()
                        if (name.isNotEmpty()) {
                            val current = countMap[name] ?: Pair(0, 0)
                            val gotten = if (goods.goodsStatus == GoodsStatus.GOTTEN.name) current.first + 1 else current.first
                            countMap[name] = Pair(gotten, current.second + 1)
                        }
                    }
                }
                _charaGoodsCountMap.value = countMap
            }
        }
    }
    fun deleteFanGoods(goods: FanGoodsEntity) {
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().delete(goods)
        }
    }

}