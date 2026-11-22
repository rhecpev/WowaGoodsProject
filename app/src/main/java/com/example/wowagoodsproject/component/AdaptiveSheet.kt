package com.example.wowagoodsproject.component

import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.sqrt

/*
 * 눌러서 여는 창(굿즈 상세 · 필터 · 캐릭터/시리즈/카테고리 고르기)의 틀.
 * - 세로 화면: 아래에서 올라오는 시트
 * - 가로 화면, 8인치 미만 기기(휴대폰·작은 태블릿) — 1번 모드: 오른쪽 절반을 덮는 패널. 왼쪽은 회색으로 가리고, 누르거나 뒤로 가기로 닫는다.
 * - 가로 화면, 8인치 이상 태블릿 — 2번 모드: 창을 열면 앱 틀(MainScreen)이 오른쪽 절반 칸([SidePaneHost])을 옆에서 밀어 넣어 거기에 연다.
 *   아무것도 안 열었으면 칸이 없고 왼쪽 화면이 전체 폭을 쓴다.
 *   왼쪽 화면은 가리지 않아 계속 누를 수 있고, 다른 굿즈를 누르면 오른쪽 칸 내용만 바뀐다.
 *   전체 화면 창 위에서 연 것은 그 창에 가려지므로 1번 모드처럼 덮는 패널로 띄운다.
 */

/** 가로 화면이면 눌러서 여는 창을 오른쪽에 띄운다 */
@Composable
fun isSidePanel(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp
}

/** 2번 모드(오른쪽 고정 칸)를 쓰는 기기 크기: 화면 대각선 8인치 이상 */
const val LARGE_TABLET_INCHES = 8f

/** 이 기기 화면의 대각선 길이(인치). 화면 실제 픽셀과 기기 dpi 로 잰다. */
fun screenDiagonalInches(context: android.content.Context): Float {
    val metrics = context.resources.displayMetrics
    val (w, h) = if (android.os.Build.VERSION.SDK_INT >= 30) {
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        val b = wm?.maximumWindowMetrics?.bounds
        if (b != null) b.width() to b.height() else metrics.widthPixels to metrics.heightPixels
    } else {
        @Suppress("DEPRECATION")
        val real = android.util.DisplayMetrics().also {
            context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.getRealMetrics(it)
        }
        if (real.widthPixels > 0) real.widthPixels to real.heightPixels else metrics.widthPixels to metrics.heightPixels
    }
    // 일부 기기는 xdpi/ydpi 를 엉터리로 알려 준다. 기기 밀도(densityDpi)와 너무 다르면 밀도를 쓴다.
    fun dpi(v: Float) = if (v > 0 && v / metrics.densityDpi in 0.67f..1.5f) v else metrics.densityDpi.toFloat()
    val wi = w / dpi(metrics.xdpi)
    val hi = h / dpi(metrics.ydpi)
    return sqrt(wi * wi + hi * hi)
}

/** 가로 화면이고 8인치 이상 태블릿이면 2번 모드(오른쪽 고정 칸) */
@Composable
fun useFixedSidePane(): Boolean {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val large = remember(context, config.screenWidthDp, config.screenHeightDp, config.densityDpi) {
        // 반올림 오차로 8인치 기기가 7.9x 로 재지는 것을 감안한다
        screenDiagonalInches(context) >= LARGE_TABLET_INCHES - 0.05f
    }
    return isSidePanel() && large
}

/** 2번 모드 오른쪽 칸이 밀고 들어오거나 접히는 시간(ms) */
const val SIDE_PANE_ANIM_MS = 260

/** 오른쪽 칸에 올라간 창 하나 */
class SidePaneEntry internal constructor(
    internal val id: Long,
    internal val onDismiss: State<() -> Unit>,
    internal val closeButton: State<Boolean>,
    internal val color: State<Color>,
    internal val content: State<@Composable ColumnScope.() -> Unit>
)

/** 2번 모드의 오른쪽 칸. 맨 나중에 연 창이 맨 위 */
@Stable
class SidePaneState internal constructor(internal val rootView: android.view.View) {
    internal val entries: SnapshotStateList<SidePaneEntry> = mutableStateListOf()
    private var nextId = 0L
    val isOpen: Boolean get() = entries.isNotEmpty()

    /** 이번에 닫힐 때 애니메이션 없이 바로 없앨지(탭 이동). 평소 닫힘은 밀려 나가며 접힌다 */
    var closeInstantly by mutableStateOf(false)
        private set

    /** 열린 창을 모두 애니메이션 없이 바로 닫는다(탭을 옮기는 것과 동시에) */
    fun closeAllNow() {
        if (!isOpen) return
        closeInstantly = true
        entries.toList().asReversed().forEach { it.onDismiss.value() }
    }
    internal fun push(
        onDismiss: State<() -> Unit>, closeButton: State<Boolean>, color: State<Color>, content: State<@Composable ColumnScope.() -> Unit>
    ): SidePaneEntry {
        // 새로 열 때는 다시 애니메이션으로 닫히게 돌려 둔다
        if (entries.isEmpty()) closeInstantly = false
        return SidePaneEntry(nextId++, onDismiss, closeButton, color, content).also { entries.add(it) }
    }
    internal fun remove(entry: SidePaneEntry) { entries.remove(entry) }
}

/** 2번 모드일 때만 앱 틀이 넣어 준다(1번 모드·세로는 null) */
val LocalSidePane = staticCompositionLocalOf<SidePaneState?> { null }

/** 지금 오른쪽 칸 안에 그려지는 중인지(칸 안에서는 고르기 창을 다시 좌우로 나누지 않는다) */
val LocalInSidePane = staticCompositionLocalOf { false }

@Composable
fun rememberSidePaneState(): SidePaneState {
    val view = LocalView.current
    return remember(view) { SidePaneState(view.rootView) }
}

/**
 * 2번 모드의 오른쪽 칸. 열린 창을 겹쳐 그린다(아래 창 상태가 사라지지 않게).
 * 뒤로 가기는 맨 위 창을 닫는다.
 */
@Composable
fun SidePaneHost(state: SidePaneState, modifier: Modifier = Modifier) {
    // 열린 창이 없으면 앱 틀이 칸 자체를 접는다.
    // 접히며 밀려 나가는 동안에도 내용이 비지 않게 마지막으로 보인 창들을 그대로 그린다.
    val live = state.entries.toList()
    val frozen = remember { arrayOf(emptyList<SidePaneEntry>()) }
    if (live.isNotEmpty()) frozen[0] = live
    val shown = frozen[0]
    if (shown.isEmpty()) return
    val top = live.lastOrNull()
    BackHandler(enabled = top != null) { top?.onDismiss?.value?.invoke() }
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalInSidePane provides true) {
            shown.forEach { entry ->
                key(entry.id) {
                    Surface(shape = RectangleShape, color = entry.color.value, modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(top = if (entry.closeButton.value) 0.dp else 12.dp)) {
                            if (entry.closeButton.value) PaneCloseRow(entry.onDismiss.value)
                            entry.content.value(this)
                        }
                    }
                }
            }
        }
        // 닫혀서 밀려 나가는 동안 남겨 둔 그림은 닫힌 굿즈의 버튼을 들고 있다. 누르지 못하게 위에서 터치를 받아 버린다.
        if (live.isEmpty()) {
            Box(
                modifier = Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            )
        }
    }
}

@Composable
private fun PaneCloseRow(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    /** 오른쪽 패널 맨 위에 닫기(×) 줄을 둘지. 내용에 제목·닫기가 따로 없는 창(굿즈 상세)이 켠다 */
    panelCloseButton: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isSidePanel()) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = dragHandle,
            // 앱 전체에서 둥근 박스를 걷어내는 흐름에 맞춰 시트 위 모서리도 각지게 둔다.
            shape = RectangleShape,
            containerColor = containerColor,
            content = content
        )
        return
    }
    // 2번 모드: 같은 창(앱 화면) 안에서 연 것만 오른쪽 고정 칸에 붙인다.
    val host = LocalSidePane.current
    val sameWindow = host != null && LocalView.current.rootView === host.rootView
    if (host != null && sameWindow) {
        val dismiss = rememberUpdatedState(onDismiss)
        val close = rememberUpdatedState(panelCloseButton)
        val color = rememberUpdatedState(containerColor)
        val body = rememberUpdatedState(content)
        DisposableEffect(host) {
            val entry = host.push(dismiss, close, color, body)
            onDispose { host.remove(entry) }
        }
        return
    }
    OverlayPanel(onDismiss, containerColor, panelCloseButton, content)
}

/** 1번 모드: 화면 오른쪽 절반을 덮는 패널(왼쪽은 회색으로 가리고, 누르면 닫힘) */
@Composable
private fun OverlayPanel(
    onDismiss: () -> Unit,
    containerColor: Color,
    panelCloseButton: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    .semantics { contentDescription = "닫기" }
            )
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)
            ) {
                Surface(shape = RectangleShape, color = containerColor, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .imePadding()
                            .padding(top = if (panelCloseButton) 0.dp else 12.dp)
                    ) {
                        if (panelCloseButton) PaneCloseRow(onDismiss)
                        content()
                    }
                }
            }
        }
    }
}
