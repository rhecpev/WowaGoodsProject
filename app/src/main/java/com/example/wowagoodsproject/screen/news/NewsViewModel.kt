package com.example.wowagoodsproject.screen.news

import com.example.wowagoodsproject.db.changed.restoreChangedGoods
import com.example.wowagoodsproject.db.character.CharaEntity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.news.NewsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NewsViewModel : ViewModel() {

    private val _news = MutableStateFlow<List<NewsEntity>>(emptyList())
    val news: StateFlow<List<NewsEntity>> = _news

    /** 아직 되돌리지 않은 보관 기록의 id 목록 */
    private val _restorableIds = MutableStateFlow<Set<Int>>(emptySet())
    val restorableIds: StateFlow<Set<Int>> = _restorableIds

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 필터 팝업에서 캐릭터 이미지/선호 여부를 보여주기 위한 전체 캐릭터 */
    private val _allCharaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val allCharaList: StateFlow<List<CharaEntity>> = _allCharaList

    private val _charaFilter = MutableStateFlow<String?>(null)
    val charaFilter: StateFlow<String?> = _charaFilter

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter

    fun setCharaFilter(chara: String?) {
        _charaFilter.value = chara
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun clearFilter() {
        _charaFilter.value = null
        _categoryFilter.value = null
    }

    init {
        viewModelScope.launch {
            App.newsDatabase.newsDao().getAllFlow().collectLatest {
                _news.value = it
            }
        }
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest {
                _allCharaList.value = it
            }
        }
        viewModelScope.launch {
            App.changedGoodsDatabase.changedGoodsDao().getPendingFlow().collectLatest { pending ->
                _restorableIds.value = pending.filter { it.canRestore }.map { it.changedId }.toSet()
            }
        }
    }

    /** 보관해 둔 원래 상태를 대체된 굿즈에 다시 적용한다. */
    fun restoreStatus(news: NewsEntity) {
        viewModelScope.launch {
            _message.value = restoreChangedGoods(news.newsChangedId)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun markAllRead() {
        viewModelScope.launch {
            App.newsDatabase.newsDao().markAllRead()
        }
    }

    fun delete(news: NewsEntity) {
        viewModelScope.launch {
            App.newsDatabase.newsDao().delete(news)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            App.newsDatabase.newsDao().deleteAll()
        }
    }
}
