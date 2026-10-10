package com.example.wowagoodsproject.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** 구매일/수령예정일 저장 형식. DatePicker 가 돌려주는 UTC 자정 밀리초와 이 문자열을 서로 변환해 쓴다. */
private const val PURCHASE_DATE_PATTERN = "yyyy-MM-dd"

private fun purchaseDateFormat() = SimpleDateFormat(PURCHASE_DATE_PATTERN, Locale.KOREA).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun formatPurchaseDate(millis: Long): String = purchaseDateFormat().format(millis)

private fun parsePurchaseDate(date: String): Long? =
    if (date.isEmpty()) null else runCatching { purchaseDateFormat().parse(date)?.time }.getOrNull()

/** 다이얼로그에서 돌려주는 값. null 인 항목은 손대지 않았다는 뜻이다(일괄 적용 때 섞인 값이 덮이지 않게). */
data class PurchaseInfoEdit(
    val purchaseStore: String?,
    val purchaseDate: String?,
    val receiveDate: String?
) {
    val isEmpty get() = purchaseStore == null && receiveDate == null && purchaseDate == null

    /** 손대지 않은 항목은 [base] 값을 그대로 쓴다. */
    fun applyTo(base: PurchaseInfo) = PurchaseInfo(
        purchaseStore = purchaseStore ?: base.purchaseStore,
        purchaseDate = purchaseDate ?: base.purchaseDate,
        receiveDate = receiveDate ?: base.receiveDate
    )
}

/**
 * 구입처/구매일/수령예정일 입력 다이얼로그.
 *
 * @param targetCount 여러 굿즈에 한 번에 적용할 때의 개수. null 이면 굿즈 한 개를 다룬다.
 * @param requireChange true 면 아무것도 바꾸지 않았을 때 적용 버튼을 막는다(일괄 적용용).
 *   false 면 빈 채로도 확인할 수 있다(구매예정으로 바꿀 때).
 * @param returnAll true 면 손대지 않은 항목도 현재 값으로 돌려준다. false 면 손대지 않은 항목은 null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseInfoDialog(
    initial: PurchaseInfo,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseInfoEdit) -> Unit,
    title: String = "구매 정보 입력",
    confirmText: String = "적용",
    targetCount: Int? = null,
    requireChange: Boolean = targetCount != null,
    returnAll: Boolean = !requireChange
) {
    var store by remember { mutableStateOf(initial.purchaseStore) }
    var receiveDate by remember { mutableStateOf(initial.receiveDate) }
    var purchaseDate by remember { mutableStateOf(initial.purchaseDate) }
    var storeTouched by remember { mutableStateOf(false) }
    var receiveTouched by remember { mutableStateOf(false) }
    var purchaseTouched by remember { mutableStateOf(false) }
    val anyTouched = storeTouched || receiveTouched || purchaseTouched

    val countNote: @Composable () -> Unit = {
        if (targetCount != null) {
            Text(
                text = "고른 ${targetCount}개 굿즈에 적용됩니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val fields: @Composable () -> Unit = {
        OutlinedTextField(
            value = store,
            onValueChange = { store = it; storeTouched = true },
            label = { Text("구입처") },
            placeholder = { Text("예: 알리익스프레스, 홍대 팝업") },
            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        DatePickField(
            label = "구매일",
            icon = Icons.Default.CalendarMonth,
            date = purchaseDate,
            onDateChange = { purchaseDate = it; purchaseTouched = true }
        )
        DatePickField(
            label = "수령예정일",
            icon = Icons.Default.MoveToInbox,
            date = receiveDate,
            onDateChange = { receiveDate = it; receiveTouched = true }
        )
    }
    val confirmButton: @Composable () -> Unit = {
        TextButton(
            onClick = {
                val all = returnAll
                onConfirm(
                    PurchaseInfoEdit(
                        purchaseStore = if (all || storeTouched) store.trim() else null,
                        receiveDate = if (all || receiveTouched) receiveDate else null,
                        purchaseDate = if (all || purchaseTouched) purchaseDate else null
                    )
                )
            },
            enabled = !requireChange || anyTouched
        ) { Text(confirmText) }
    }
    val dismissButton: @Composable () -> Unit = {
        TextButton(onClick = onDismiss) { Text("취소") }
    }

    // 가로 화면처럼 낮은 화면에서는 제목·입력칸·버튼을 위아래로 쌓으면 화면을 넘친다.
    // 그때는 제목을 왼쪽, 입력칸과 버튼을 오른쪽에 두어 높이를 줄인다.
    if (LocalConfiguration.current.screenHeightDp < PURCHASE_DIALOG_STACKED_HEIGHT_DP) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(0.9f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.34f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        countNote()
                    }
                    Column(modifier = Modifier.weight(0.66f)) {
                        // 아주 낮은 화면에서도 버튼이 밀려나지 않도록 입력칸 쪽만 스크롤할 수 있게 둔다.
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) { fields() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            dismissButton()
                            confirmButton()
                        }
                    }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                countNote()
                fields()
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

/** 구매 정보 입력창을 위아래로 쌓아도 다 들어가는 최소 화면 높이. 이보다 낮으면 좌우로 나눈다. */
private const val PURCHASE_DIALOG_STACKED_HEIGHT_DP = 480

/**
 * 기기 언어와 상관없이 안쪽 내용을 한국어 설정으로 그린다.
 * 앱 화면은 전부 한국어인데 달력만 기기 언어(예: 영어의 "October")로 나오는 것을 맞추려고 쓴다.
 */
@Composable
private fun KoreanLocale(content: @Composable () -> Unit) {
    val baseConfiguration = LocalConfiguration.current
    val baseContext = LocalContext.current
    val configuration = remember(baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(Locale.KOREAN) }
    }
    val context = remember(baseContext, configuration) { baseContext.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalContext provides context,
        content = content
    )
}

/** 달력 창이 잘리지 않고 다 들어가는 최소 화면 높이(달력 창 568dp + 위아래 여유) */
private const val DATE_PICKER_FULL_HEIGHT_DP = 640

/** 달력으로만 고르는 값이라 입력창 대신 누를 수 있는 줄로 보여준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickField(
    label: String,
    icon: ImageVector,
    date: String,
    onDateChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        // 달력의 요일 계산은 달력 상태가 만들어질 때의 언어를 따르므로, 상태도 한국어 설정 안에서 만든다.
        KoreanLocale {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = parsePurchaseDate(date)
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { onDateChange(formatPurchaseDate(it)) }
                            showDatePicker = false
                        }
                    ) { Text("확인") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("취소") }
                }
            ) {
                // 다이얼로그는 안쪽에서 기기 언어 설정을 다시 깔기 때문에, 달력 바로 바깥에서 한 번 더 한국어로 바꾼다.
                KoreanLocale {
                    // 달력 창은 세로 568dp 를 전제로 만들어져 있어, 가로 화면처럼 낮은 화면에서는 달력이 눌리고 잘린다.
                    // 낮은 화면에서는 위쪽 제목·선택 날짜 줄을 빼서 높이를 줄이고, 그래도 모자란 만큼은 스크롤로 본다.
                    if (LocalConfiguration.current.screenHeightDp < DATE_PICKER_FULL_HEIGHT_DP) {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            title = null,
                            headline = null,
                            showModeToggle = false
                        )
                    } else {
                        DatePicker(state = datePickerState)
                    }
                }
            }
        }
    }

    Surface(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date.ifEmpty { "날짜 선택" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (date.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            if (date.isNotEmpty()) {
                IconButton(onClick = { onDateChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "$label 지우기")
                }
            }
        }
    }
}
