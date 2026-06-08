package com.example.wowagoodsproject.screen.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.series.SeriesEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SeriesViewModel : ViewModel() {

    private val _seriesList = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val seriesList: StateFlow<List<SeriesEntity>> = _seriesList

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _charaMap = MutableStateFlow<Map<String, CharaEntity>>(emptyMap())
    val charaMap: StateFlow<Map<String, CharaEntity>> = _charaMap

    private val _charaGoodsCountMap = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val charaGoodsCountMap: StateFlow<Map<String, Pair<Int, Int>>> = _charaGoodsCountMap

    private val _allCharaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val allCharaList: StateFlow<List<CharaEntity>> = _allCharaList

    private val _selectedCharaFilter = MutableStateFlow<CharaEntity?>(null)
    val selectedCharaFilter: StateFlow<CharaEntity?> = _selectedCharaFilter

    private val _selectedSeries = MutableStateFlow<SeriesEntity?>(null)
    val selectedSeries: StateFlow<SeriesEntity?> = _selectedSeries

    private val _seriesGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val seriesGoods: StateFlow<List<GoodsEntity>> = _seriesGoods

    private val _seriesCharaCountMap = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val seriesCharaCountMap: StateFlow<Map<String, Pair<Int, Int>>> = _seriesCharaCountMap

    private val _filteredSeriesList = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val filteredSeriesList: StateFlow<List<SeriesEntity>> = _filteredSeriesList

    val countries = listOf("전체", "중국", "대만", "한국", "일본", "미국")

    init {
        loadCharaMap()
        viewModelScope.launch {
            App.seriesDatabase.seriesDao().getAllFlow()
                .combine(App.database.goodsDao().getAllFlow()) { series, goods ->
                    Pair(series, goods)
                }
                .collectLatest { (series, _) ->
                    _seriesList.value = series
                    updateFilteredList()
                    loadSeriesCharaCount()
                    loadCharaGoodsCount()
                }
        }
    }

    fun loadCharaMap() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest { charaList ->
                _allCharaList.value = charaList
                _charaMap.value = charaList.associateBy { it.charaNm }
            }
        }
    }

    private suspend fun loadCharaGoodsCount() {
        val allGoods = App.database.goodsDao().getAll()
        val countMap = mutableMapOf<String, Pair<Int, Int>>()
        allGoods.forEach { goods ->
            goods.goodsChara.split(",").forEach { chara ->
                val name = chara.trim()
                val current = countMap[name] ?: Pair(0, 0)
                val gotten = if (goods.goodsIsGotten) current.first + 1 else current.first
                val total = current.second + 1
                countMap[name] = Pair(gotten, total)
            }
        }
        _charaGoodsCountMap.value = countMap
    }

    private suspend fun loadSeriesCharaCount() {
        val countMap = mutableMapOf<String, Pair<Int, Int>>()
        _seriesList.value.forEach { series ->
            series.seriesCharas.split(",").forEach { chara ->
                val name = chara.trim()
                if (name.isNotEmpty()) {
                    val total = App.database.goodsDao().countBySeriesAndChara(series.seriesNm, name)
                    val gotten = App.database.goodsDao().countGottenBySeriesAndChara(series.seriesNm, name)
                    countMap["${series.seriesNm}|$name"] = Pair(gotten, total)
                }
            }
        }
        _seriesCharaCountMap.value = countMap
    }

    fun updateFilteredList() {
        val country = countries[_selectedTab.value]
        val byCountry = if (country == "전체") _seriesList.value
        else _seriesList.value.filter { it.seriesCountry == country }
        val charaFilter = _selectedCharaFilter.value
        _filteredSeriesList.value = if (charaFilter == null) byCountry
        else byCountry.filter { series ->
            series.seriesCharas.split(",").map { it.trim() }.contains(charaFilter.charaNm)
        }
    }
    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
        updateFilteredList()
    }

    fun setCharaFilter(chara: CharaEntity?) {
        _selectedCharaFilter.value = chara
        updateFilteredList()
    }

    fun selectSeries(series: SeriesEntity) {
        _selectedSeries.value = series
        viewModelScope.launch {
            _seriesGoods.value = App.database.goodsDao().getBySeries(series.seriesNm)
        }
    }

    fun clearSelectedSeries() {
        _selectedSeries.value = null
        _seriesGoods.value = emptyList()
    }

    fun toggleGotten(goods: GoodsEntity) {
        viewModelScope.launch {
            val updated = goods.copy(goodsIsGotten = !goods.goodsIsGotten)
            App.database.goodsDao().update(updated)
            _selectedSeries.value?.let {
                _seriesGoods.value = App.database.goodsDao().getBySeries(it.seriesNm)
            }
            loadSeriesCharaCount()
        }
    }

    fun getCharasForSeries(series: SeriesEntity): List<CharaEntity> {
        if (series.seriesCharas.isEmpty()) return emptyList()
        return series.seriesCharas.split(",").mapNotNull { _charaMap.value[it.trim()] }
    }

    fun getCharaGoodsCount(charaNm: String): Pair<Int, Int> {
        return _charaGoodsCountMap.value[charaNm] ?: Pair(0, 0)
    }

    fun getSeriesCharaCount(seriesNm: String, charaNm: String): Pair<Int, Int> {
        return _seriesCharaCountMap.value["$seriesNm|$charaNm"] ?: Pair(0, 0)
    }
}