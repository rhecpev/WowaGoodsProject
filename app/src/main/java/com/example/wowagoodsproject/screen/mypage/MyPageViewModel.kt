package com.example.wowagoodsproject.screen.mypage

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.content.Intent
import com.example.wowagoodsproject.UpdateManager
import com.example.wowagoodsproject.component.GoodsStatus

data class OfficialGoodsBackup(
    val goodsSeries: String,
    val goodsChara: String,
    val goodsCategory: String,
    val goodsStatus: String = GoodsStatus.NOT_GOTTEN.name,
    val goodsMemo: String = "",
    val goodsPrice: String = ""
)

class MyPageViewModel : ViewModel() {

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _officialGottenGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val officialGottenGoods: StateFlow<List<GoodsEntity>> = _officialGottenGoods

    private val _allSeriesGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val allSeriesGoods: StateFlow<List<GoodsEntity>> = _allSeriesGoods

    private val _fanGottenGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGottenGoods: StateFlow<List<FanGoodsEntity>> = _fanGottenGoods

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _currentSection = MutableStateFlow<String?>(null)
    val currentSection: StateFlow<String?> = _currentSection

    private val _selectedCharaFilter = MutableStateFlow<String?>(null)
    val selectedCharaFilter: StateFlow<String?> = _selectedCharaFilter

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter

    private val _showFilterDialog = MutableStateFlow(false)
    val showFilterDialog: StateFlow<Boolean> = _showFilterDialog


    private val _latestVersion = MutableStateFlow<String?>(null)
    val latestVersion: StateFlow<String?> = _latestVersion

    init {
        loadCharaList()
        loadGottenGoods()
        checkLatestVersion()
    }

    private fun checkLatestVersion() {
        viewModelScope.launch {
            val result = UpdateManager.checkAppUpdate()
            if (result != null) {
                _latestVersion.value = result.first
            } else {
                // 하루 안 지났으면 SharedPreferences에서 캐시된 값 읽기
                val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
                _latestVersion.value = prefs.getString("cached_latest_version", null)
            }
        }
    }

    fun loadCharaList() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest {
                _charaList.value = it
            }
        }
    }

    fun loadGottenGoods() {
        viewModelScope.launch {
            val allGoods = App.database.goodsDao().getAll()
            val setMemos =
                allGoods.filter { it.goodsCategory == CATEGORY_SET }.map { it.goodsMemo }.toSet()

            val gottenGoods = allGoods.filter { goods ->
                when {
                    goods.goodsCategory == CATEGORY_SET -> {
                        allGoods.any { it.goodsCategory != CATEGORY_SET && it.goodsMemo == goods.goodsMemo && it.goodsSeries == goods.goodsSeries && it.goodsStatus != GoodsStatus.NOT_GOTTEN.name }
                    }
                    goods.goodsMemo.isNotEmpty() && goods.goodsMemo in setMemos && goods.goodsCategory != CATEGORY_SET -> goods.goodsStatus != GoodsStatus.NOT_GOTTEN.name
                    else -> goods.goodsStatus != GoodsStatus.NOT_GOTTEN.name
                }
            }

            _officialGottenGoods.value = gottenGoods
            val seriesList = gottenGoods.map { it.goodsSeries }.distinct()
            _allSeriesGoods.value = seriesList.flatMap {
                App.database.goodsDao().getBySeries(it)
            }
            _fanGottenGoods.value =
                App.fanDatabase.fanGoodsDao().getAll().filter { it.fanGoodsIsGotten }
        }
    }

    fun toggleFavorite(chara: CharaEntity) {
        viewModelScope.launch {
            val updated = chara.copy(charaIsFavorite = !chara.charaIsFavorite)
            App.charaDatabase.charaDao().update(updated)
        }
    }
    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN
            val updated = goods.copy(goodsStatus = newStatus.name)
            App.database.goodsDao().update(updated)

            if (updated.goodsCategory != CATEGORY_SET && updated.goodsMemo.isNotEmpty()) {
                val allGoods = App.database.goodsDao().getBySeries(updated.goodsSeries)
                val siblings = allGoods.filter {
                    it.goodsCategory != CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                val setGoods = allGoods.find {
                    it.goodsCategory == CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                setGoods?.let { set ->
                    val newIsGotten = siblings.all { it.goodsStatus == GoodsStatus.GOTTEN.name }
                    App.database.goodsDao().update(set.copy(goodsStatus = if (newIsGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name))
                }
            }

            loadGottenGoods()
        }
    }

    fun setOfficialPending(goods: GoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING
            val updated = goods.copy(goodsStatus = newStatus.name)
            App.database.goodsDao().update(updated)

            if (updated.goodsCategory != CATEGORY_SET && updated.goodsMemo.isNotEmpty()) {
                val allGoods = App.database.goodsDao().getBySeries(updated.goodsSeries)
                val siblings = allGoods.filter {
                    it.goodsCategory != CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                val setGoods = allGoods.find {
                    it.goodsCategory == CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                setGoods?.let { set ->
                    val newIsGotten = siblings.all { it.goodsStatus == GoodsStatus.GOTTEN.name }
                    App.database.goodsDao().update(set.copy(goodsStatus = if (newIsGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name))
                }
            }

            loadGottenGoods()
        }
    }

    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN
            val updated = goods.copy(fanGoodsStatus = newStatus.name)
            App.fanDatabase.fanGoodsDao().update(updated)
            loadGottenGoods()
        }
    }

    fun setFanPending(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING
            val updated = goods.copy(fanGoodsStatus = newStatus.name)
            App.fanDatabase.fanGoodsDao().update(updated)
            loadGottenGoods()
        }
    }


    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setSection(section: String?) {
        _currentSection.value = section
    }

    fun setCharaFilter(chara: String?) {
        _selectedCharaFilter.value = chara
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setShowFilterDialog(show: Boolean) {
        _showFilterDialog.value = show
    }


    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val gson = Gson()
                    val fanGoods = App.fanDatabase.fanGoodsDao().getAll()
                    val fanGoodsJson = gson.toJson(fanGoods)
                    val officialGoods = App.database.goodsDao().getAll()
                    val officialBackup = officialGoods.map {
                        OfficialGoodsBackup(
                            goodsSeries = it.goodsSeries,
                            goodsChara = it.goodsChara,
                            goodsCategory = it.goodsCategory,
                            goodsStatus = it.goodsStatus,
                            goodsMemo = it.goodsMemo,
                            goodsPrice = it.goodsPrice
                        )
                    }
                    val officialJson = gson.toJson(officialBackup)
                    val charas = App.charaDatabase.charaDao().getAll()
                    val favoriteCharas = charas.filter { it.charaIsFavorite }.map { it.charaNm }
                    val charaJson = gson.toJson(favoriteCharas)

                    val zipFile = File(context.filesDir, "wowa_backup.zip")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                        zip.putNextEntry(ZipEntry("fan_goods.json"))
                        zip.write(fanGoodsJson.toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("official_gotten.json"))
                        zip.write(officialJson.toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("favorite_charas.json"))
                        zip.write(charaJson.toByteArray())
                        zip.closeEntry()
                        fanGoods.forEach { goods ->
                            if (goods.fanGoodsImgPath.isNotEmpty()) {
                                val imgFile = File(goods.fanGoodsImgPath)
                                if (imgFile.exists()) {
                                    zip.putNextEntry(ZipEntry("images/${imgFile.name}"))
                                    FileInputStream(imgFile).use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val contentValues = android.content.ContentValues().apply {
                            put(
                                android.provider.MediaStore.Downloads.DISPLAY_NAME,
                                "wowa_backup.zip"
                            )
                            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                        }
                        val resolver = context.contentResolver
                        val downloadUri = resolver.insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        downloadUri?.let { uri ->
                            resolver.openOutputStream(uri)?.use { output ->
                                FileInputStream(zipFile).copyTo(output)
                            }
                        }
                    } else {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            zipFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "백업 파일 내보내기").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                    zipFile.delete()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "추출 완료!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "추출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val gson = Gson()
                    var fanGoodsJson: String? = null
                    var officialJson: String? = null
                    var favoriteCharaJson: String? = null
                    val imageMap = mutableMapOf<String, ByteArray>()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                when {
                                    entry.name == "fan_goods.json" -> fanGoodsJson =
                                        zip.bufferedReader().readText()

                                    entry.name == "official_gotten.json" -> officialJson =
                                        zip.bufferedReader().readText()

                                    entry.name == "favorite_charas.json" -> favoriteCharaJson =
                                        zip.bufferedReader().readText()

                                    entry.name.startsWith("images/") -> {
                                        val fileName = entry.name.removePrefix("images/")
                                        imageMap[fileName] = zip.readBytes()
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                    imageMap.forEach { (fileName, bytes) ->
                        val imgFile = File(context.filesDir, fileName)
                        imgFile.writeBytes(bytes)
                    }
                    fanGoodsJson?.let { json ->
                        val type = object : TypeToken<List<FanGoodsEntity>>() {}.type
                        val fanGoods: List<FanGoodsEntity> = gson.fromJson(json, type)
                        App.fanDatabase.fanGoodsDao().deleteAll()
                        fanGoods.forEach { goods ->
                            val newPath = if (goods.fanGoodsImgPath.isNotEmpty()) {
                                val fileName = File(goods.fanGoodsImgPath).name
                                File(context.filesDir, fileName).absolutePath
                            } else ""
                            App.fanDatabase.fanGoodsDao().insert(
                                goods.copy(fanGoodsId = 0, fanGoodsImgPath = newPath)
                            )
                        }
                    }
                    officialJson?.let { json ->
                        val type = object : TypeToken<List<OfficialGoodsBackup>>() {}.type
                        val backups: List<OfficialGoodsBackup> = gson.fromJson(json, type)
                        App.database.goodsDao().resetAllGotten()
                        backups.forEach { backup ->
                            val goods = App.database.goodsDao().getByUniqueKey(
                                backup.goodsSeries,
                                backup.goodsChara,
                                backup.goodsCategory,
                                backup.goodsMemo,
                                backup.goodsPrice
                            )
                            goods?.let {
                                App.database.goodsDao()
                                    .update(it.copy(goodsStatus = backup.goodsStatus))
                            }
                        }
                    }
                    favoriteCharaJson?.let { json ->
                        val type = object : TypeToken<List<String>>() {}.type
                        val favoriteCharas: List<String> = gson.fromJson(json, type)
                        val allCharas = App.charaDatabase.charaDao().getAll()
                        allCharas.forEach { chara ->
                            App.charaDatabase.charaDao().update(
                                chara.copy(charaIsFavorite = favoriteCharas.contains(chara.charaNm))
                            )
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "입력 완료!", Toast.LENGTH_SHORT).show()
                }
                loadGottenGoods()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "입력 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


}