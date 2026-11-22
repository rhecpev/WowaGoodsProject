package com.example.wowagoodsproject.screen.mypage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.Composable
import com.example.wowagoodsproject.component.ProgressDialog

/*
 * 데이터 입력 · 추출 화면 묶음. 명조 마이페이지, 배신 굿즈 장르 마이페이지, 장르 목록의 복구가 모두 같은 것을 쓴다.
 * 화면마다 따로 만들지 말고 여기 것을 쓴다(모양·순서·진행 표시가 어긋나지 않게).
 */

/**
 * 데이터 입력: 파일 읽는 중(게이지) → 불러올 내용 확인 → 데이터 입력 중(게이지).
 * 끝나면 [ImportResultBus] 로 결과 보고서가 뜬다(뷰모델이 부른다).
 */
@Composable
fun ImportFlow(
    /** 고른 파일을 읽는 중이면 0~1 */
    inspectProgress: Float?,
    /** 확인 시트에 보일 파일과 비교. null 이면 닫힘 */
    plan: Pair<android.net.Uri, ImportPlan>?,
    isImporting: Boolean,
    importProgress: Float,
    onImport: (android.net.Uri, Set<ImportPart>) -> Unit,
    onDismiss: () -> Unit,
    /** 배신 굿즈 줄의 이름 */
    betrayTitle: String = "배신 굿즈도 함께 불러오기",
    /** 처음에 켜 둘 항목. null 이면 파일 기본값 */
    initialSelection: Set<ImportPart>? = null
) {
    inspectProgress?.let { f ->
        ProgressDialog(title = "파일 읽는 중", fraction = f, icon = Icons.Default.FileUpload, label = "불러올 내용을 정리하고 있어요")
    }
    plan?.let { (uri, p) ->
        ImportPreviewSheet(
            plan = p,
            betrayTitle = betrayTitle,
            initialSelection = initialSelection ?: p.defaultSelection,
            onImport = { selected -> onImport(uri, selected) },
            onDismiss = onDismiss
        )
    }
    if (isImporting) {
        ProgressDialog(title = "데이터 입력 중", fraction = importProgress, icon = Icons.Default.FileUpload)
    }
}

/**
 * 데이터 추출: 추출할 데이터 확인(단계별) → 데이터 추출 중(게이지).
 * [genres] 가 있으면 '전체 추출 / 장르 선택'을 고를 수 있다(명조 마이페이지). 장르 마이페이지는 비워 두고 [selection] 을 그 장르로.
 */
@Composable
fun ExportFlow(
    show: Boolean,
    preview: ExportPreview?,
    selection: ExportSelection,
    isExporting: Boolean,
    exportProgress: Float,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    genres: List<MyPageViewModel.ExportGenre> = emptyList(),
    onSelectionChange: (ExportSelection) -> Unit = {}
) {
    if (show) {
        ExportPreviewSheet(
            preview = preview,
            genres = genres,
            selection = selection,
            onSelectionChange = onSelectionChange,
            onExport = onExport,
            onDismiss = onDismiss
        )
    }
    if (isExporting) {
        ProgressDialog(title = "데이터 추출 중", fraction = exportProgress, icon = Icons.Default.FileDownload)
    }
}

/** 추출이 끝났을 때 알림 문구. 파일에 담긴 것만 적는다. */
fun exportDoneMessage(written: ExportPreview): String {
    val parts = buildList {
        // 명조 파일이 들어 있으면(공식 굿즈 데이터 줄이 있으면) 명조 항목을 적는다.
        if (written.officialTotal > 0 || written.fanTotal > 0 || written.favorites.isNotEmpty()) {
            add("공식 보유 ${written.officialGotten} · 구매예정 ${written.officialPending}")
            add("비공식 ${written.fanTotal}")
            add("선호 캐릭터 ${written.favorites.size}명")
        }
        if (written.betrayGenres > 0) add("배신 굿즈 ${written.betrayGenres}개 장르 · 굿즈 ${written.betrayGoods}개")
        if (written.photoCount > 0) add("사진 ${written.photoCount}장")
    }
    return "추출 완료! " + parts.joinToString(", ")
}
