package com.example.wowagoodsproject.screen.fan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FanAddViewModel : ViewModel() {

    private val _price = MutableStateFlow("")
    val price: StateFlow<String> = _price

    private val _series = MutableStateFlow("")
    val series: StateFlow<String> = _series

    private val _selectedCharas = MutableStateFlow<List<String>>(emptyList())
    val selectedCharas: StateFlow<List<String>> = _selectedCharas

    private val _category = MutableStateFlow("")
    val category: StateFlow<String> = _category

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _categoryList = MutableStateFlow<List<String>>(emptyList())
    val categoryList: StateFlow<List<String>> = _categoryList

    private val _isGotten = MutableStateFlow(false)
    val isGotten: StateFlow<Boolean> = _isGotten

    private val _memo = MutableStateFlow("")
    val memo: StateFlow<String> = _memo

    init {
        loadCharaList()
        loadCategoryList()
    }

    private fun loadCharaList() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest {
                _charaList.value = it
            }
        }
    }

    private fun loadCategoryList() {
        viewModelScope.launch {
            val fanCategories = App.fanDatabase.fanGoodsDao().getFanCategories()
            val officialCategories = App.database.goodsDao().getAllCategories()
            _categoryList.value = (fanCategories + officialCategories).distinct().sorted()
        }
    }

    fun onPriceChange(value: String) { _price.value = value }
    fun onSeriesChange(value: String) { _series.value = value }
    fun onCategoryChange(value: String) { _category.value = value }
    fun onImageSelected(uri: Uri?) { _imageUri.value = uri }
    fun onIsGottenChange(value: Boolean) { _isGotten.value = value }
    fun onMemoChange(value: String) { _memo.value = value }

    fun toggleChara(chara: String) {
        val current = _selectedCharas.value.toMutableList()
        if (current.contains(chara)) {
            current.remove(chara)
        } else {
            current.add(chara)
        }
        _selectedCharas.value = current
    }

    private suspend fun copyImageToInternalStorage(context: Context, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "fan_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        }
    }

    fun insert(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val imgPath = _imageUri.value?.let {
                copyImageToInternalStorage(context, it)
            } ?: ""

            App.fanDatabase.fanGoodsDao().insert(
                FanGoodsEntity(
                    fanGoodsReleaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    fanGoodsSeries = _series.value,
                    fanGoodsPrice = _price.value.toIntOrNull() ?: 0,
                    fanGoodsChara = _selectedCharas.value.joinToString(","),
                    fanGoodsCategory = _category.value,
                    fanGoodsImgPath = imgPath,
                    fanGoodsIsGotten = _isGotten.value,
                    fanGoodsMemo = _memo.value
                )
            )
            onSuccess()
        }
    }
}