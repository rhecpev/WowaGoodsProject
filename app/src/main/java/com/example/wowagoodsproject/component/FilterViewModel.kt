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

    fun setFilter(filter: FilterType) {
        _filterType.value = filter
    }

    fun <T : GoodsItem> applyFilter(list: List<T>): List<T> {
        return when (_filterType.value) {
            FilterType.ALL -> list
            FilterType.GOTTEN -> list.filter { it.isGotten }
            FilterType.NOT_GOTTEN -> list.filter { !it.isGotten }
        }
    }
}