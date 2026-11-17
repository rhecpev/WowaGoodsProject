package com.example.wowagoodsproject.screen.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.patchnote.PatchNoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PatchNotesViewModel : ViewModel() {

    private val _patchNotes = MutableStateFlow<List<PatchNoteEntity>>(emptyList())
    val patchNotes: StateFlow<List<PatchNoteEntity>> = _patchNotes

    init {
        viewModelScope.launch {
            App.patchNoteDatabase.patchNoteDao().getAllFlow().collectLatest {
                _patchNotes.value = it
            }
        }
    }

    /** 업데이트 이력을 모두 지워 용량을 비운다. 마이페이지의 '마지막 업데이트' 표시는 설정 값이라 그대로 남는다. */
    fun clearAll() {
        viewModelScope.launch { App.patchNoteDatabase.patchNoteDao().deleteAll() }
    }
}