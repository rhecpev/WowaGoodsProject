package com.example.wowagoodsproject.screen.betray

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.db.betray.BetrayCharaEntity
import com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.applyStatus
import com.example.wowagoodsproject.db.betray.deleteGenreWithGoods
import com.example.wowagoodsproject.db.betray.deleteWithImage
import com.example.wowagoodsproject.db.betray.deleteSeriesWithImages
import com.example.wowagoodsproject.db.betray.reorderFavorites
import com.example.wowagoodsproject.db.betray.savePending
import com.example.wowagoodsproject.db.betray.setQuantity
import com.example.wowagoodsproject.db.betray.setShipping
import com.example.wowagoodsproject.db.betray.toggleFavorite
import com.example.wowagoodsproject.db.betray.toggleGotten
import com.example.wowagoodsproject.db.betray.togglePending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.widget.Toast
import com.example.wowagoodsproject.screen.mypage.BackupExport
import com.example.wowagoodsproject.screen.mypage.ExportPreview
import com.example.wowagoodsproject.screen.mypage.ExportSelection

/** 장르 목록 보기 방식: 배경 사진 그리드 / 아이콘 원 */
enum class GenreViewMode { GRID, CIRCLE }

/** 장르 목록 화면. 장르마다 굿즈 수·보유 수를 함께 보여 준다. */
class BetrayGenresViewModel : ViewModel() {
    private val dao = App.betrayDatabase.betrayDao()
    private val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)

    private val _viewMode = MutableStateFlow(
        GenreViewMode.entries.firstOrNull { it.name == prefs.getString(VIEW_MODE_KEY, null) } ?: GenreViewMode.GRID
    )
    val viewMode: StateFlow<GenreViewMode> = _viewMode

    fun setViewMode(mode: GenreViewMode) {
        _viewMode.value = mode
        prefs.edit().putString(VIEW_MODE_KEY, mode.name).apply()
    }

    data class GenreRow(val genre: BetrayGenreEntity, val total: Int, val gotten: Int)

    private val _rows = MutableStateFlow<List<GenreRow>?>(null)
    /** null 이면 읽는 중 */
    val rows: StateFlow<List<GenreRow>?> = _rows

    init {
        viewModelScope.launch {
            dao.genresFlow().collect { genres ->
                val goods = dao.allGoods().groupBy { it.genreId }
                _rows.value = genres.map { g ->
                    val list = goods[g.genreId].orEmpty()
                    GenreRow(g, list.size, list.count { it.status == GoodsStatus.GOTTEN })
                }
            }
        }
    }

    /** 장르 목록은 굿즈가 바뀌어도 다시 세야 하므로 화면에 돌아올 때 부른다. */
    fun refresh() {
        viewModelScope.launch {
            val goods = dao.allGoods().groupBy { it.genreId }
            _rows.value = dao.genres().map { g ->
                val list = goods[g.genreId].orEmpty()
                GenreRow(g, list.size, list.count { it.status == GoodsStatus.GOTTEN })
            }
        }
    }

    // ── 장르 복구 ──
    // 받는 zip 은 두 가지: (1) 명조 마이페이지 전체 추출(명조 + 배신 굿즈), (2) 장르 마이페이지 추출(그 장르만).
    // 어느 쪽이든 불러오기 전에 항목별 변경 사항을 보여 주고 고른 것만 불러온다. 배신 굿즈는 합치기(다른 장르는 그대로).
    private val _restorePlan = MutableStateFlow<Pair<android.net.Uri, com.example.wowagoodsproject.screen.mypage.ImportPlan>?>(null)
    /** 복구 확인 시트에 보일 파일과 비교. null 이면 닫힘 */
    val restorePlan: StateFlow<Pair<android.net.Uri, com.example.wowagoodsproject.screen.mypage.ImportPlan>?> = _restorePlan
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring
    private val _restoreProgress = MutableStateFlow(0f)
    val restoreProgress: StateFlow<Float> = _restoreProgress

    /** 고른 파일을 읽는 중이면 0~1, 아니면 null */
    private val _inspectProgress = MutableStateFlow<Float?>(null)
    val inspectProgress: StateFlow<Float?> = _inspectProgress

    fun inspectRestore(context: Context, uri: android.net.Uri) {
        if (_inspectProgress.value != null) return
        _inspectProgress.value = 0f
        viewModelScope.launch {
            try {
                val plan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val size = com.example.wowagoodsproject.screen.mypage.fileSizeOf(context, uri)
                    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("파일을 열 수 없습니다")
                    input.use {
                        com.example.wowagoodsproject.screen.mypage.ImportPreviewer.inspect(it, betrayMerge = true, totalBytes = size) { f -> _inspectProgress.value = f }
                    }
                }
                if (plan.section(com.example.wowagoodsproject.screen.mypage.ImportPart.BETRAY) == null) {
                    android.widget.Toast.makeText(App.appContext, "이 파일에는 배신 굿즈 장르가 없어요", android.widget.Toast.LENGTH_LONG).show()
                } else _restorePlan.value = uri to plan
            } catch (e: Exception) {
                android.widget.Toast.makeText(App.appContext, "복구 실패: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _inspectProgress.value = null
            }
        }
    }

    fun dismissRestore() { _restorePlan.value = null }

    /** 고른 항목만 불러온다. 배신 굿즈는 합치기로(같은 이름 장르는 바뀌고 없는 장르는 더해진다). */
    fun restore(context: Context, uri: android.net.Uri, selected: Set<com.example.wowagoodsproject.screen.mypage.ImportPart>) {
        _restorePlan.value = null
        _isRestoring.value = true
        _restoreProgress.value = 0f
        viewModelScope.launch {
            try {
                val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("파일을 열 수 없습니다")
                    input.use {
                        com.example.wowagoodsproject.screen.mypage.BackupZip.restore(
                            it, context.filesDir, com.example.wowagoodsproject.screen.mypage.RestoreParts.of(selected, betrayMerge = true)
                        ) { p -> _restoreProgress.value = p }
                    }
                }
                com.example.wowagoodsproject.screen.mypage.ImportResultBus.show(report)
                refresh()
            } catch (e: Exception) {
                android.widget.Toast.makeText(App.appContext, "복구 실패: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isRestoring.value = false
            }
        }
    }

    fun deleteGenre(genre: BetrayGenreEntity) {
        viewModelScope.launch { dao.deleteGenreWithGoods(genre.genreId) }
    }

    /** 목록에서 끌어 바꾼 순서([ids] 앞에서부터)를 저장한다. 목록에 없는 장르는 그 뒤에 원래 순서대로 둔다. */
    fun setOrder(ids: List<Int>) {
        viewModelScope.launch {
            val list = dao.genres()
            val pos = ids.withIndex().associate { it.value to it.index }
            list.sortedWith(compareBy<BetrayGenreEntity> { pos[it.genreId] ?: Int.MAX_VALUE }.thenBy { it.genreOrder })
                .forEachIndexed { i, g -> if (g.genreOrder != i + 1) dao.updateGenre(g.copy(genreOrder = i + 1)) }
        }
    }

    private companion object {
        const val VIEW_MODE_KEY = "betray_genre_view_mode"
    }
}

/** 한 장르 안의 모든 탭이 함께 쓰는 상태와 쓰기 작업 */
class BetrayViewModel(val genreId: Int) : ViewModel() {
    private val dao = App.betrayDatabase.betrayDao()

    val genre: StateFlow<BetrayGenreEntity?> = dao.genresFlow()
        .map { genres -> genres.find { it.genreId == genreId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val goods: StateFlow<List<BetrayGoodsEntity>> = dao.goodsFlow(genreId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<BetrayFavoriteEntity>> = dao.favoritesFlow(genreId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 등록한 시리즈(이름·배경·아이콘) */
    val series: StateFlow<List<com.example.wowagoodsproject.db.betray.BetraySeriesEntity>> = dao.seriesFlow(genreId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteSeries(name: String) = launch { dao.deleteSeriesWithImages(genreId, name) }

    /** 직접 추가한 캐릭터(이름·사진) */
    val charas: StateFlow<List<BetrayCharaEntity>> = dao.charasFlow(genreId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleGotten(g: BetrayGoodsEntity) = launch { dao.toggleGotten(g.goodsId) }
    fun togglePending(g: BetrayGoodsEntity) = launch { dao.togglePending(g.goodsId) }
    fun savePending(g: BetrayGoodsEntity, info: PurchaseInfo) = launch { dao.savePending(g.goodsId, info) }
    fun setQuantity(g: BetrayGoodsEntity, q: Int) = launch { dao.setQuantity(g.goodsId, q) }
    fun setShipping(g: BetrayGoodsEntity, date: String) = launch { dao.setShipping(g.goodsId, date) }
    fun applyStatus(targets: List<BetrayGoodsEntity>, status: GoodsStatus, edit: PurchaseInfoEdit?) =
        launch { dao.applyStatus(targets.map { it.goodsId }, status, edit) }
    fun delete(g: BetrayGoodsEntity) = launch { dao.deleteWithImage(g) }
    fun toggleFavorite(charaNm: String) = launch { dao.toggleFavorite(genreId, charaNm) }
    fun reorderFavorites(names: List<String>) = launch { dao.reorderFavorites(genreId, names) }
    // ── 이 장르만 추출 ──
    private val _exportPreview = MutableStateFlow<ExportPreview?>(null)
    /** 장르 추출 확인 창에 보여 줄 내용. null 이면 세는 중 */
    val exportPreview: StateFlow<ExportPreview?> = _exportPreview
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting
    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress

    fun loadExportPreview() {
        _exportPreview.value = null
        viewModelScope.launch { _exportPreview.value = ExportPreview.load(ExportSelection.genreOnly(genreId)) }
    }

    fun exportGenre(context: Context) {
        if (_isExporting.value) return
        val name = genre.value?.genreNm.orEmpty()
        _isExporting.value = true
        _exportProgress.value = 0f
        viewModelScope.launch {
            try {
                // 파일 이름: wuwaGoods_버전_날짜_장르이름.zip
                val written = BackupExport.export(context, ExportSelection.genreOnly(genreId)) { _exportProgress.value = it }
                Toast.makeText(App.appContext, com.example.wowagoodsproject.screen.mypage.exportDoneMessage(written), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(App.appContext, "추출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    // ── 이 장르로 데이터 입력 ──
    // 파일에서 이 장르와 이름이 같은 장르만 꺼내 합치기로 불러온다. 명조 데이터·다른 장르는 건드리지 않는다.
    private val _importPlan = MutableStateFlow<Pair<android.net.Uri, com.example.wowagoodsproject.screen.mypage.ImportPlan>?>(null)
    /** 불러오기 확인 시트에 보일 파일과 비교. null 이면 닫힘 */
    val importPlan: StateFlow<Pair<android.net.Uri, com.example.wowagoodsproject.screen.mypage.ImportPlan>?> = _importPlan
    private val _importInspect = MutableStateFlow<Float?>(null)
    /** 고른 파일을 읽는 중이면 0~1, 아니면 null */
    val importInspect: StateFlow<Float?> = _importInspect
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting
    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress

    fun inspectImport(context: Context, uri: android.net.Uri) {
        if (_importInspect.value != null) return
        val name = genre.value?.genreNm ?: return
        _importInspect.value = 0f
        viewModelScope.launch {
            try {
                val plan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val size = com.example.wowagoodsproject.screen.mypage.fileSizeOf(context, uri)
                    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("파일을 열 수 없습니다")
                    input.use {
                        com.example.wowagoodsproject.screen.mypage.ImportPreviewer.inspect(it, betrayMerge = true, onlyGenre = name, totalBytes = size) { f -> _importInspect.value = f }
                    }
                }
                _importPlan.value = uri to plan
            } catch (e: Exception) {
                Toast.makeText(App.appContext, "입력 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _importInspect.value = null
            }
        }
    }

    fun dismissImport() { _importPlan.value = null }

    fun importData(context: Context, uri: android.net.Uri) {
        val name = genre.value?.genreNm ?: return
        _importPlan.value = null
        _isImporting.value = true
        _importProgress.value = 0f
        viewModelScope.launch {
            try {
                val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("파일을 열 수 없습니다")
                    input.use {
                        com.example.wowagoodsproject.screen.mypage.BackupZip.restore(
                            it, context.filesDir, com.example.wowagoodsproject.screen.mypage.RestoreParts.BETRAY_MERGE.copy(onlyGenre = name)
                        ) { p -> _importProgress.value = p }
                    }
                }
                com.example.wowagoodsproject.screen.mypage.ImportResultBus.show(report)
            } catch (e: Exception) {
                Toast.makeText(App.appContext, "입력 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isImporting.value = false
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
