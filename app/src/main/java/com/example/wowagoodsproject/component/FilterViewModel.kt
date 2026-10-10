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

    /**
     * 세트 굿즈는 목록에 노출하지 않으므로 낱개 굿즈 상태만 판정한다.
     * [type] 은 화면이 collectAsState 로 읽은 값을 넘긴다. 여기서 흐름의 현재 값을 직접 읽으면
     * 필터가 바뀌어도 화면이 다시 계산하지 않아 목록이 그대로 남는다.
     */
    fun <T : GoodsItem> applyFilter(list: List<T>, type: FilterType): Pair<List<T>, List<T>> {
        val filtered = when (type) {
            FilterType.ALL -> list
            FilterType.GOTTEN -> list.filter { it.isGotten }
            FilterType.NOT_GOTTEN -> list.filter { it.status == GoodsStatus.NOT_GOTTEN }
            FilterType.PENDING -> list.filter { it.status == GoodsStatus.PENDING }
        }
        return Pair(list, filtered)
    }
}