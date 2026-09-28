package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (targetCount != null) {
                    Text(
                        text = "고른 ${targetCount}개 굿즈에 적용됩니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

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
            DatePicker(state = datePickerState)
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
