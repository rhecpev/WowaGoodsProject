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

    fun <T : GoodsItem> applyFilter(list: List<T>, charaFilter: String? = null): Pair<List<T>, List<T>> {
        val filtered = when (_filterType.value) {
            FilterType.ALL -> list
            FilterType.GOTTEN -> list.filter { goods ->
                if (goods.category == CATEGORY_SET) {
                    val components = list.filter {
                        it.category != CATEGORY_SET && it.memo == goods.memo && it.series == goods.series
                    }
                    val target = if (charaFilter != null) components.filter { it.chara.contains(charaFilter) } else components
                    if (charaFilter != null) target.isNotEmpty() && target.any { it.isGotten }
                    else target.isNotEmpty() && target.all { it.isGotten }
                } else {
                    goods.isGotten
                }
            }
            FilterType.NOT_GOTTEN -> list.filter { goods ->
                if (goods.category == CATEGORY_SET) {
                    val components = list.filter {
                        it.category != CATEGORY_SET && it.memo == goods.memo && it.series == goods.series
                    }
                    val target = if (charaFilter != null) components.filter { it.chara.contains(charaFilter) } else components
                    target.any { it.status == GoodsStatus.NOT_GOTTEN }
                } else {
                    goods.status == GoodsStatus.NOT_GOTTEN
                }
            }
            FilterType.PENDING -> list.filter { goods ->
                if (goods.category == CATEGORY_SET) {
                    val components = list.filter {
                        it.category != CATEGORY_SET && it.memo == goods.memo && it.series == goods.series
                    }
                    val target = if (charaFilter != null) components.filter { it.chara.contains(charaFilter) } else components
                    target.any { it.status == GoodsStatus.PENDING }
                } else {
                    goods.status == GoodsStatus.PENDING
                }
            }
        }
        return Pair(list, filtered)
    }
}