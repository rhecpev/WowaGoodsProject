package com.example.wowagoodsproject.component

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ListModeViewModel : ViewModel() {

    private val _isGridMode = MutableStateFlow(false)
    val isGridMode: StateFlow<Boolean> = _isGridMode

    fun toggleGridMode() {
        _isGridMode.value = !_isGridMode.value
    }
}