package com.example.wowagoodsproject.screen.mypage

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
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

data class OfficialGoodsBackup(
    val goodsSeries: String,
    val goodsChara: String,
    val goodsCategory: String,
    val goodsIsGotten: Boolean,
    val goodsMemo: String = ""
)
class MyPageViewModel : ViewModel() {

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _officialGottenGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val officialGottenGoods: StateFlow<List<GoodsEntity>> = _officialGottenGoods

    private val _fanGottenGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGottenGoods: StateFlow<List<FanGoodsEntity>> = _fanGottenGoods

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _currentSection = MutableStateFlow<String?>(null)
    val currentSection: StateFlow<String?> = _currentSection

    private val _selectedCharaFilter = MutableStateFlow<String?>(null)
    val selectedCharaFilter: StateFlow<String?> = _selectedCharaFilter

    private val _showCharaFilterDialog = MutableStateFlow(false)
    val showCharaFilterDialog: StateFlow<Boolean> = _showCharaFilterDialog

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus

    init {
        loadCharaList()
        loadGottenGoods()
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
            _officialGottenGoods.value = App.database.goodsDao().getAll().filter { it.goodsIsGotten }
            _fanGottenGoods.value = App.fanDatabase.fanGoodsDao().getAll().filter { it.fanGoodsIsGotten }
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
            val updated = goods.copy(goodsIsGotten = !goods.goodsIsGotten)
            App.database.goodsDao().update(updated)
            loadGottenGoods()
        }
    }

    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val updated = goods.copy(fanGoodsIsGotten = !goods.fanGoodsIsGotten)
            App.fanDatabase.fanGoodsDao().update(updated)
            loadGottenGoods()
        }
    }

    fun setSelectedTab(tab: Int) { _selectedTab.value = tab }
    fun setSection(section: String?) { _currentSection.value = section }
    fun setCharaFilter(chara: String?) { _selectedCharaFilter.value = chara }
    fun setShowCharaFilterDialog(show: Boolean) { _showCharaFilterDialog.value = show }
    fun clearUpdateStatus() { _updateStatus.value = null }

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
                            goodsIsGotten = it.goodsIsGotten,
                            goodsMemo = it.goodsMemo  // 추가
                        )
                    }
                    val officialJson = gson.toJson(officialBackup)
                    val zipFile = File(context.filesDir, "wowa_backup.zip")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                        zip.putNextEntry(ZipEntry("fan_goods.json"))
                        zip.write(fanGoodsJson.toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("official_gotten.json"))
                        zip.write(officialJson.toByteArray())
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
                            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "wowa_backup.zip")
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
                    val imageMap = mutableMapOf<String, ByteArray>()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                when {
                                    entry.name == "fan_goods.json" -> fanGoodsJson = zip.bufferedReader().readText()
                                    entry.name == "official_gotten.json" -> officialJson = zip.bufferedReader().readText()
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
                                backup.goodsMemo
                            )
                            goods?.let {
                                App.database.goodsDao().update(
                                    it.copy(goodsIsGotten = backup.goodsIsGotten)
                                )
                            }
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

    fun updateCharacters() {
        viewModelScope.launch {
            try {
                _updateStatus.value = "업데이트 중..."
                val result = withContext(Dispatchers.IO) {
                    UpdateManager.updateCharacters()
                }
                val msg = buildString {
                    if (result.first > 0) append("${result.first}명 추가 ")
                    if (result.second > 0) append("${result.second}명 업데이트 ")
                    if (result.third > 0) append("${result.third}명 삭제 ")
                    if (isEmpty()) append("이미 캐릭터가 최신 상태예요!")
                    else append("완료!")
                }
                _updateStatus.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _updateStatus.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateGoods(){
        viewModelScope.launch {
            try {
                _updateStatus.value = "업데이트 중..."
                val result = withContext(Dispatchers.IO) {
                    UpdateManager.updateSeries()
                }
                val result2 = withContext(Dispatchers.IO) {
                    UpdateManager.updateGoods()
                }
                val msg = buildString {
                    if (result.first > 0) append("${result.first}개 시리즈 추가 ")
                    if (result.second > 0) append("${result.second}개 시리즈 업데이트 ")
                    if (result.third > 0) append("${result.third}개 시리즈 삭제 ")
                    if (isEmpty()) append("이미 시리즈가 최신 상태예요!")
                    else append("완료!")
                }
                val msg2 = buildString {
                    if (result2.first > 0) append("${result.first}개 굿즈 추가 ")
                    if (result2.second > 0) append("${result.second}개 굿즈 업데이트 ")
                    if (result2.third > 0) append("${result.third}개 굿즈 삭제 ")
                    if (isEmpty()) append("이미 굿즈가 최신 상태예요!")
                    else append("완료!")
                }
                _updateStatus.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, msg, Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, msg2, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _updateStatus.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



//    fun updateGoods() {
//        viewModelScope.launch {
//            try {
//                _updateStatus.value = "업데이트 중..."
//                val result = withContext(Dispatchers.IO) {
//                    val gson = Gson()
//                    val seriesUrl = java.net.URL("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/series.json")
//                    val seriesConnection = seriesUrl.openConnection() as java.net.HttpURLConnection
//                    seriesConnection.requestMethod = "GET"
//                    seriesConnection.connectTimeout = 10000
//                    seriesConnection.readTimeout = 10000
//                    val seriesJson = seriesConnection.inputStream.bufferedReader().readText()
//                    val seriesType = object : TypeToken<List<com.example.wowagoodsproject.db.series.SeriesEntity>>() {}.type
//                    val remoteSeries: List<com.example.wowagoodsproject.db.series.SeriesEntity> = gson.fromJson(seriesJson, seriesType)
//                    val localSeries = App.seriesDatabase.seriesDao().getAll()
//
//                    var seriesCount = 0
//                    remoteSeries.forEach { remote ->
//                        val local = localSeries.find {
//                            it.seriesNm == remote.seriesNm && it.seriesCountry == remote.seriesCountry
//                        }
//                        if (local == null) {
//                            App.seriesDatabase.seriesDao().insert(remote.copy(seriesId = 0))
//                            seriesCount++
//                        } else if (
//                            local.seriesUrl != remote.seriesUrl ||
//                            local.seriesCharas != remote.seriesCharas ||
//                            local.seriesDate != remote.seriesDate
//                        ) {
//                            App.seriesDatabase.seriesDao().update(
//                                local.copy(
//                                    seriesUrl = remote.seriesUrl,
//                                    seriesCharas = remote.seriesCharas,
//                                    seriesDate = remote.seriesDate
//                                )
//                            )
//                            seriesCount++
//                        }
//                    }
//
//                    val goodsUrl = java.net.URL("https://raw.githubusercontent.com/rhecpev/wuwa-goods-data/refs/heads/main/goods.json")
//                    val goodsConnection = goodsUrl.openConnection() as java.net.HttpURLConnection
//                    goodsConnection.requestMethod = "GET"
//                    goodsConnection.connectTimeout = 10000
//                    goodsConnection.readTimeout = 10000
//                    val goodsJson = goodsConnection.inputStream.bufferedReader().readText()
//                    val goodsType = object : TypeToken<List<GoodsEntity>>() {}.type
//                    val remoteGoods: List<GoodsEntity> = gson.fromJson(goodsJson, goodsType)
//                    val localGoods = App.database.goodsDao().getAll()
//
//                    var goodsCount = 0
//                    remoteGoods.forEach { remote ->
//                        val local = localGoods.find {
//                            it.goodsSeries == remote.goodsSeries &&
//                                    it.goodsChara == remote.goodsChara &&
//                                    it.goodsCategory == remote.goodsCategory
//                        }
//                        if (local == null) {
//                            App.database.goodsDao().insert(remote.copy(goodsId = 0, goodsIsGotten = false))
//                            goodsCount++
//                        } else if (
//                            local.goodsUrl != remote.goodsUrl ||
//                            local.goodsPrice != remote.goodsPrice
//                        ) {
//                            App.database.goodsDao().update(
//                                local.copy(
//                                    goodsUrl = remote.goodsUrl,
//                                    goodsPrice = remote.goodsPrice
//                                )
//                            )
//                            goodsCount++
//                        }
//                    }
//                    Pair(seriesCount, goodsCount)
//                }
//                val msg = buildString {
//                    if (result.first > 0) append("시리즈 ${result.first}개 ")
//                    if (result.second > 0) append("굿즈 ${result.second}개 ")
//                    if (isEmpty()) append("이미 최신 상태예요!")
//                    else append("업데이트됨!")
//                }
//                _updateStatus.value = null
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(App.appContext, msg, Toast.LENGTH_SHORT).show()
//                }
//            } catch (e: Exception) {
//                _updateStatus.value = null
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(App.appContext, "업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
}