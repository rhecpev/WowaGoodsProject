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
}