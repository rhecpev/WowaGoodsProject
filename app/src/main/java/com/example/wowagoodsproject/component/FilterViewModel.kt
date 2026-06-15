package com.example.wowagoodsproject.component

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class FilterType {
    ALL, GOTTEN, NOT_GOTTEN
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

    fun <T : GoodsItem> applyFilter(list: List<T>): Pair<List<T>, List<T>> {
        val filtered = when (_filterType.value) {
            FilterType.ALL -> list
            FilterType.GOTTEN -> list.filter { goods ->
                if (goods.category == CATEGORY_SET) {
                    list.any { it.category != CATEGORY_SET && it.memo == goods.memo && it.series == goods.series && it.isGotten }
                } else {
                    goods.isGotten
                }
            }
            FilterType.NOT_GOTTEN -> list.filter { goods ->
                if (goods.category == CATEGORY_SET) {
                    list.any { it.category != CATEGORY_SET && it.memo == goods.memo && it.series == goods.series && !it.isGotten }                } else {
                    !goods.isGotten
                }
            }
        }
        return Pair(list, filtered)
    }
}