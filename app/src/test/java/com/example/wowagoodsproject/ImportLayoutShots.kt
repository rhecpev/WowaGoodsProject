package com.example.wowagoodsproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.withStatus
import com.example.wowagoodsproject.screen.mypage.*
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * 불러올 내용 확인 · 불러오기 보고서를 가로 휴대폰 · 가로 태블릿 · 세로 휴대폰 크기로 그려 build/layout-shots/ 에 남긴다(눈으로 확인용).
 * 예시 데이터는 실제와 비슷한 양으로 만든다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportLayoutShots {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before fun setUp() = TestDb.setUp()
    @After fun tearDown() = TestDb.tearDown()

    private val charas = listOf("치사", "카르티시아", "에이메스", "데니아", "수수", "금희", "장리", "카멜리아")

    private fun samplePlan(): ImportPlan {
        val rows = charas.mapIndexed { i, n -> DiffRow(n, "", 10 + i * 3, 12 + i * 4) }
        val goods = (1..30).map { i ->
            ReviewGoods(official = i % 3 != 0, series = "2.${i % 5}버전", chara = charas[i % charas.size], category = listOf("키링", "아크릴", "카드")[i % 3],
                price = "${i * 1000}원", memo = "", status = if (i % 4 == 0) GoodsStatus.PENDING else GoodsStatus.GOTTEN, quantity = 1)
        }
        val gotten = (1..40).map { i ->
            FanGoodsEntity(fanGoodsId = i, fanGoodsSeries = "s", fanGoodsChara = charas[i % 5]).withStatus(GoodsStatus.GOTTEN.name)
        }
        val charaEntities = charas.mapIndexed { i, n -> CharaEntity(charaId = i + 1, charaNm = n, charaIsFavorite = i < 4, charaFavoriteOrder = i + 1) }
        val graph = FileGraph("보유 굿즈", gotten, charaEntities, charas.take(4), charas.associateWith { 3 to 10 }, "선호 캐릭터 공식 수집현황")
        val betrayGraph = graph.copy(title = "보유 굿즈", genreName = "원신", betray = true, collectionTitle = "선호 캐릭터 수집률")
        return ImportPlan(
            listOf(
                ImportSection(ImportPart.OFFICIAL, "보유 120 · 구매예정 8", "보유 131 · 구매예정 9", changed = true,
                    details = listOf("상태가 바뀌는 굿즈 12개"), rows = rows, pendingRows = rows.take(3)),
                ImportSection(ImportPart.FAN, "40개", "43개", changed = true, rows = rows.take(4),
                    goodsRows = (1..6).map { GoodsChangeRow("코믹 · 아크릴 $it", "치사", "", GoodsStatus.PENDING, GoodsStatus.GOTTEN) }),
                ImportSection(ImportPart.FAVORITES, "4명", "5명", changed = true,
                    favRows = charas.mapIndexed { i, n -> FavRow(n, "", i < 4, i < 5) }),
                ImportSection(ImportPart.BETRAY, "장르 1개 · 굿즈 2개", "장르 2개 · 굿즈 9개", changed = true,
                    genres = listOf(GenreDiff("원신", "굿즈 2개 → 9개", rows.take(3), charas.take(6).mapIndexed { i, n -> FavRow(n, "", i < 2, i < 3) })))
            ),
            ImportReport(
                officialGotten = 131, officialPending = 9, fanTotal = 43, photos = 52,
                favorites = charas.take(5).map { ReportChara(it, "", 10, 2) },
                charas = charas.map { ReportChara(it, "", 10, 2) },
                betrayGenres = listOf("원신" to 9),
                goods = goods,
                betrayReview = listOf(ExportPreview.BetrayReview(1, "원신", goods.take(9), emptyMap())),
                graphs = listOf(graph, betrayGraph)
            )
        )
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val window = ShadowDialog.getLatestDialog()?.window ?: compose.activity.window
        val view = window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        val dir = File("build/layout-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun wizardSteps(prefix: String) {
        compose.setContent { WowaGoodsProjectTheme { ImportPreviewSheet(samplePlan(), onImport = {}, onDismiss = {}) } }
        compose.waitForIdle()
        for (i in 1..5) {
            shot("${prefix}_import_$i")
            val next = compose.onAllNodesWithText("다음").fetchSemanticsNodes()
            if (next.isEmpty()) break
            // 단계마다 확인 체크가 있어야 넘어가므로 모두 확인한다
            runCatching { compose.onAllNodesWithText("모두 확인").onLast().performClick() }
            runCatching { compose.onAllNodesWithText("이 선호 캐릭터 목록이 맞아요").onLast().performClick() }
            compose.waitForIdle()
            compose.onAllNodesWithText("다음").onLast().performClick()
            compose.waitForIdle()
        }
    }

    private fun report(prefix: String) {
        val before = ImportSnapshot(officialGotten = 120, officialPending = 8, fanTotal = 40, fanGotten = 30, favorites = 4, betrayGoods = 2,
            betrayGotten = 1, betrayByGenre = mapOf("원신" to 2), gottenByChara = charas.associateWith { 5 })
        val after = before.copy(officialGotten = 131, officialPending = 9, fanTotal = 43, fanGotten = 33, favorites = 5, betrayGoods = 9, betrayGotten = 4,
            betrayByGenre = mapOf("원신" to 9, "붕괴" to 3), gottenByChara = charas.mapIndexed { i, n -> n to 5 + i }.toMap())
        compose.setContent {
            WowaGoodsProjectTheme {
                ImportResultDialog("불러오기 보고서", RestoreReport(131, 129, 43, 5, betrayGoods = 12, betrayGenres = listOf("원신", "붕괴"), before = before, after = after), onDismiss = {})
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        shot("${prefix}_report")
    }

    @Test fun 가로_휴대폰() {
        RuntimeEnvironment.setQualifiers("w891dp-h411dp-land")
        wizardSteps("phone_land")
    }

    @Test fun 가로_휴대폰_보고서() {
        RuntimeEnvironment.setQualifiers("w891dp-h411dp-land")
        report("phone_land")
    }

    @Test fun 가로_태블릿() {
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land")
        wizardSteps("tablet_land")
    }

    @Test fun 가로_태블릿_보고서() {
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land")
        report("tablet_land")
    }

    @Test fun 세로_휴대폰() {
        RuntimeEnvironment.setQualifiers("w400dp-h860dp-port")
        wizardSteps("phone_port")
    }

    @Test fun 세로_휴대폰_보고서() {
        RuntimeEnvironment.setQualifiers("w400dp-h860dp-port")
        report("phone_port")
    }

    @Test fun 가로_휴대폰_같은_굿즈_창() {
        RuntimeEnvironment.setQualifiers("w891dp-h411dp-land")
        val dup = (1..3).map { FanGoodsEntity(fanGoodsId = it, fanGoodsSeries = "코믹", fanGoodsChara = "치사", fanGoodsCategory = "아크릴", fanGoodsPrice = "3000원") }
        compose.setContent { WowaGoodsProjectTheme { com.example.wowagoodsproject.component.DuplicateGoodsDialog(dup, onAddAnyway = {}, onDismiss = {}) } }
        shot("phone_land_duplicate")
    }

    @Test fun 가로_휴대폰_굿즈_상세() {
        RuntimeEnvironment.setQualifiers("w891dp-h411dp-land")
        compose.setContent {
            WowaGoodsProjectTheme {
                com.example.wowagoodsproject.component.GoodsDetailDialog(
                    imgPath = "", series = "코믹", chara = "치사", category = "아크릴", price = "3000원", isGotten = false, isPending = true,
                    memo = "메모", onDismiss = {}, onToggleGotten = {}, onSetPending = {}, onDelete = {},
                    purchaseInfo = com.example.wowagoodsproject.component.PurchaseInfo("타오바오", "2026-11-20", ""),
                    onSavePendingInfo = {}, onToggleShipping = {}, quantity = 1, onQuantityChange = {}, onEdit = {}
                )
            }
        }
        shot("phone_land_detail")
    }

    @Test fun 가로_휴대폰_필터() {
        RuntimeEnvironment.setQualifiers("w891dp-h411dp-land")
        compose.setContent {
            WowaGoodsProjectTheme {
                com.example.wowagoodsproject.component.GoodsFilterDialog(
                    widthSizeClass = androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded,
                    charaList = charas.mapIndexed { i, n -> CharaEntity(charaId = i + 1, charaNm = n) },
                    categoryList = listOf("키링", "아크릴", "카드"),
                    selectedCharaFilter = null, selectedCategoryFilter = null,
                    onCharaSelect = {}, onCategorySelect = {}, onClearFilter = {}, onDismiss = {}
                )
            }
        }
        shot("phone_land_filter")
    }
}
