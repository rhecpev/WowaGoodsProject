package com.example.wowagoodsproject.component

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class FilterType {
    ALL, GOTTEN, NOT_GOTTEN, PENDING
}
class FilterViewModel : ViewModel() {

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType

    private val _selectedCharaFilter = MutableStateFlow<String?>(null)
    val selectedCharaFilter: StateFlow<String?> = _selectedCharaFilter

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter

    fun setFilter(filter: FilterType) {
        _filterType.value = filter
    }

    fun setCharaFilter(chara: String?) {
        _selectedCharaFilter.value = chara
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun clearGoodsFilter() {
        _selectedCharaFilter.value = null
        _selectedCategoryFilter.value = null
    }

    /** 세트 굿즈는 목록에 노출하지 않으므로 낱개 굿즈 상태만 판정한다. */
    fun <T : GoodsItem> applyFilter(list: List<T>): Pair<List<T>, List<T>> {
        val filtered = when (_filterType.value) {
            FilterType.ALL -> list
            FilterType.GOTTEN -> list.filter { it.isGotten }
            FilterType.NOT_GOTTEN -> list.filter { it.status == GoodsStatus.NOT_GOTTEN }
            FilterType.PENDING -> list.filter { it.status == GoodsStatus.PENDING }
        }
        return Pair(list, filtered)
    }
}