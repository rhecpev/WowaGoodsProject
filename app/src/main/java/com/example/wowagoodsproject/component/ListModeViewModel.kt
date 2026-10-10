package com.example.wowagoodsproject.component

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.wowagoodsproject.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 한 화면의 보기 방식(리스트/그리드)과 그리드 한 줄 개수.
 * [screenKey] 별로 따로 저장해서, 화면마다 다르게 두어도 앱을 다시 켰을 때 그대로다.
 */
class ListModeViewModel(private val screenKey: String = "default") : ViewModel() {

    private val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)

    private val _isGridMode = MutableStateFlow(prefs.getBoolean("grid_mode_$screenKey", false))
    val isGridMode: StateFlow<Boolean> = _isGridMode

    /** 사용자가 정한 한 줄 개수. 아직 안 정했으면 0 이고, 그때는 화면 너비에 맞춰 자동으로 놓는다. */
    private val _gridColumns = MutableStateFlow(prefs.getInt("grid_columns_$screenKey", 0))
    val gridColumns: StateFlow<Int> = _gridColumns

    fun toggleGridMode() {
        _isGridMode.value = !_isGridMode.value
        prefs.edit().putBoolean("grid_mode_$screenKey", _isGridMode.value).apply()
    }

    fun setGridColumns(columns: Int) {
        val value = columns.coerceIn(GRID_COLUMNS_MIN, GRID_COLUMNS_MAX)
        if (value == _gridColumns.value) return
        _gridColumns.value = value
        prefs.edit().putInt("grid_columns_$screenKey", value).apply()
    }
}
