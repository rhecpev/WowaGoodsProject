package com.example.wowagoodsproject.screen.mypage

import androidx.room.withTransaction
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.goodsStatusOf
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.deleteUnusedImages
import com.example.wowagoodsproject.db.fan.withStatus
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.withStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * 유저 데이터 백업(zip) 만들기와 불러오기.
 *
 * zip 안의 파일
 * - official_gotten.json: 공식 굿즈 한 줄씩([OfficialGoodsBackup])
 * - fan_goods.json: 2차창작 굿즈([FanGoodsBackup])
 * - favorite_charas.json: 선호 캐릭터 이름 목록 (2.1부터)
 * - images/: 2차창작 사진
 *
 * 버전마다 공식 굿즈 줄의 칸이 다르다. 불러오기는 아래를 모두 받아야 한다.
 * - 1.0~2.0: 시리즈·캐릭터·카테고리·goodsIsGotten (+메모, 1.1~1.2는 메모 없음). 가격·상태 칸이 없다.
 * - 2.1: 상태·가격·구매일·구입처
 * - 2.2~2.5: 수령예정일·수량·배송 시작일
 * - 3.0: 굿즈 번호(goodsUid)
 */

/**
 * 백업 파일에 담기는 공식 굿즈 한 줄.
 *
 * 모든 칸이 nullable 인 이유: 예전 버전에서 만든 zip 에는 나중에 생긴 칸(상태·메모·구매 정보)이
 * 아예 없고, Gson 은 그 자리에 코틀린 기본값 대신 null 을 넣는다.
 * 그대로 엔티티에 옮기면 non-null 칸에 null 이 들어가 복원이 통째로 실패한다.
 */
data class OfficialGoodsBackup(
    val goodsSeries: String? = null,
    val goodsChara: String? = null,
    val goodsCategory: String? = null,
    val goodsStatus: String? = null,
    val goodsMemo: String? = null,
    val goodsPrice: String? = null,
    val goodsPurchaseDate: String? = null,
    val goodsPurchaseStore: String? = null,
    val goodsReceiveDate: String? = null,
    val goodsQuantity: Int? = null,
    val goodsShippingDate: String? = null,
    /** 원본 데이터의 굿즈 번호. 이 칸이 생기기 전 백업이거나 0 이면 내용으로 짝을 찾는다. */
    val goodsUid: Int? = null,
    /** 2.0까지 쓰던 보유 여부. 상태 칸이 없는 백업은 이것으로 보유를 되살린다. 옛 버전 앱이 읽을 수 있게 새 백업에도 적는다. */
    val goodsIsGotten: Boolean? = null
) {
    /** 되살릴 상태. 상태 칸이 없으면 예전 보유 여부로, 모르는 값이면 미보유로 본다. */
    val restoredStatus: GoodsStatus
        get() = goodsStatus?.let { goodsStatusOf(it) }
            ?: if (goodsIsGotten == true) GoodsStatus.GOTTEN else GoodsStatus.NOT_GOTTEN

    /** 보유·구매예정이라 되살릴 것이 있는 줄. 불러오기는 먼저 모든 굿즈를 미보유로 비우므로 나머지는 볼 필요가 없다. */
    val isOwned: Boolean get() = restoredStatus != GoodsStatus.NOT_GOTTEN

    fun applyTo(goods: GoodsEntity): GoodsEntity = goods.withStatus(restoredStatus.name).copy(
        goodsPurchaseDate = goodsPurchaseDate.orEmpty(),
        goodsPurchaseStore = goodsPurchaseStore.orEmpty(),
        goodsReceiveDate = goodsReceiveDate.orEmpty(),
        goodsQuantity = (goodsQuantity ?: 1).coerceAtLeast(1),
        goodsShippingDate = if (restoredStatus == GoodsStatus.PENDING) goodsShippingDate.orEmpty() else ""
    )

    companion object {
        fun of(goods: GoodsEntity) = OfficialGoodsBackup(
            goodsSeries = goods.goodsSeries,
            goodsChara = goods.goodsChara,
            goodsCategory = goods.goodsCategory,
            goodsStatus = goods.goodsStatus,
            goodsMemo = goods.goodsMemo,
            goodsPrice = goods.goodsPrice,
            goodsPurchaseDate = goods.goodsPurchaseDate,
            goodsPurchaseStore = goods.goodsPurchaseStore,
            goodsReceiveDate = goods.goodsReceiveDate,
            goodsQuantity = goods.goodsQuantity,
            goodsShippingDate = goods.goodsShippingDate,
            goodsUid = goods.goodsUid,
            goodsIsGotten = goods.goodsStatus == GoodsStatus.GOTTEN.name
        )
    }
}

/**
 * 백업 줄마다 되살릴 폰의 굿즈를 찾는다. 한 굿즈는 한 줄에만 짝지어진다.
 * 보유·구매예정 줄만 본다(미보유 줄이 느슨한 비교에서 다른 줄의 짝을 가로채지 않게).
 *
 * 1. 굿즈 번호가 같으면 같은 굿즈다.
 * 2. 시리즈·캐릭터·카테고리가 같고, 백업에 있는 칸(메모·가격)이 모두 같은 굿즈가 하나뿐이면 그 굿즈다.
 *    옛 백업은 메모·가격 칸이 없을 수 있다. 메모가 없는데 후보가 여럿이면 메모가 빈 굿즈 하나를 고른다.
 * 3. 백업 뒤에 데이터의 가격이나 메모가 바뀐 경우: 메모나 가격 중 하나라도 같은 굿즈가 하나뿐이면 그 굿즈다.
 *    둘 다 다르면 다른 굿즈일 수 있어 짝짓지 않는다.
 */
internal fun matchOfficialBackups(
    backups: List<OfficialGoodsBackup>,
    local: List<GoodsEntity>
): List<Pair<OfficialGoodsBackup, GoodsEntity>> {
    val owned = backups.filter { it.isOwned }
    val matched = linkedMapOf<Int, Pair<OfficialGoodsBackup, GoodsEntity>>() // 백업 줄 순번 → 짝
    val claimed = mutableSetOf<Int>() // 짝지어진 폰 굿즈 id

    fun claim(index: Int, goods: GoodsEntity) {
        matched[index] = owned[index] to goods
        claimed += goods.goodsId
    }

    val byUid = local.filter { it.goodsUid > 0 }.associateBy { it.goodsUid }
    owned.forEachIndexed { i, b ->
        val goods = (b.goodsUid ?: 0).takeIf { it > 0 }?.let { byUid[it] } ?: return@forEachIndexed
        if (goods.goodsId !in claimed) claim(i, goods)
    }

    val bySeriesKey = local.groupBy { Triple(it.goodsSeries, it.goodsChara, it.goodsCategory) }
    fun candidates(b: OfficialGoodsBackup) =
        bySeriesKey[Triple(b.goodsSeries.orEmpty(), b.goodsChara.orEmpty(), b.goodsCategory.orEmpty())]
            .orEmpty()
            .filter { it.goodsId !in claimed }

    owned.forEachIndexed { i, b ->
        if (i in matched) return@forEachIndexed
        var found = candidates(b).filter {
            (b.goodsMemo == null || it.goodsMemo == b.goodsMemo) && (b.goodsPrice == null || it.goodsPrice == b.goodsPrice)
        }
        if (found.size > 1 && b.goodsMemo == null) found = found.filter { it.goodsMemo.isEmpty() }
        found.singleOrNull()?.let { claim(i, it) }
    }

    owned.forEachIndexed { i, b ->
        if (i in matched || b.goodsMemo == null || b.goodsPrice == null) return@forEachIndexed
        val pool = candidates(b)
        (pool.filter { it.goodsMemo == b.goodsMemo }.singleOrNull()
            ?: pool.filter { it.goodsPrice == b.goodsPrice }.singleOrNull())
            ?.let { claim(i, it) }
    }

    return matched.values.toList()
}

/** 백업 파일에 담기는 2차창작 굿즈. nullable 인 이유는 [OfficialGoodsBackup] 과 같다. */
data class FanGoodsBackup(
    val fanGoodsReleaseDate: String? = null,
    val fanGoodsSeries: String? = null,
    /** 1.1~2.0 백업은 숫자(0 이면 안 적음)로 들어 있다. Gson 이 문자열로 읽는다. */
    val fanGoodsPrice: String? = null,
    val fanGoodsChara: String? = null,
    val fanGoodsCategory: String? = null,
    val fanGoodsImgPath: String? = null,
    val fanGoodsIsGotten: Boolean? = null,
    val fanGoodsStatus: String? = null,
    val fanGoodsMemo: String? = null,
    val fanGoodsPurchaseDate: String? = null,
    val fanGoodsPurchaseStore: String? = null,
    val fanGoodsReceiveDate: String? = null,
    val fanGoodsQuantity: Int? = null,
    val fanGoodsShippingDate: String? = null
) {
    /** @param imgPath 백업 안 이미지를 앱 폴더에 풀어 놓은 뒤의 경로 */
    fun toEntity(imgPath: String): FanGoodsEntity {
        // 상태 칸이 생기기 전 백업이면 보유 여부로 상태를 되살린다. 모르는 상태 값이면 미보유로 받는다.
        val status = fanGoodsStatus?.let { goodsStatusOf(it).name }
            ?: if (fanGoodsIsGotten == true) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name
        return FanGoodsEntity(
            fanGoodsId = 0,
            fanGoodsReleaseDate = fanGoodsReleaseDate.orEmpty(),
            fanGoodsSeries = fanGoodsSeries.orEmpty(),
            // 옛 백업의 숫자 0 은 가격을 안 적은 것이다.
            fanGoodsPrice = fanGoodsPrice.orEmpty().let { if (it == "0") "" else it },
            fanGoodsChara = fanGoodsChara.orEmpty(),
            fanGoodsCategory = fanGoodsCategory.orEmpty(),
            fanGoodsImgPath = imgPath,
            fanGoodsMemo = fanGoodsMemo.orEmpty(),
            fanGoodsPurchaseDate = fanGoodsPurchaseDate.orEmpty(),
            fanGoodsPurchaseStore = fanGoodsPurchaseStore.orEmpty(),
            fanGoodsReceiveDate = fanGoodsReceiveDate.orEmpty(),
            fanGoodsQuantity = (fanGoodsQuantity ?: 1).coerceAtLeast(1),
            fanGoodsShippingDate = fanGoodsShippingDate.orEmpty()
        ).withStatus(status) // 예전 칸 fanGoodsIsGotten·수량·배송 시작일을 상태에 맞춘다
    }
}

/** 불러오기 결과. 화면에 몇 개를 되살렸는지 알려 준다. */
data class RestoreReport(
    /** 백업에 있던 보유·구매예정 공식 굿즈 수. 백업에 공식 굿즈 파일이 없으면 null */
    val ownedInBackup: Int?,
    /** 그중 지금 데이터에서 찾아 되살린 수 */
    val restored: Int,
    /** 2차창작 굿즈 수. 백업에 2차창작이 없으면 null */
    val fanGoods: Int?,
    /** 선호 캐릭터 수. 백업에 선호 캐릭터가 없으면(2.0까지) null */
    val favorites: Int?
) {
    val missing: Int get() = (ownedInBackup ?: 0) - restored

    fun message(): String = listOfNotNull(
        ownedInBackup?.let { owned ->
            "보유·구매예정 ${owned}개 중 ${restored}개" + if (missing > 0) " (${missing}개는 지금 데이터에서 찾지 못함)" else ""
        },
        fanGoods?.let { "2차창작 ${it}개" },
        favorites?.let { "선호 캐릭터 ${it}명" }
    ).joinToString(", ", prefix = "입력 완료! ")
}

object BackupZip {

    private const val OFFICIAL = "official_gotten.json"
    private const val FAN = "fan_goods.json"
    private const val FAVORITES = "favorite_charas.json"
    private const val IMAGES = "images"

    /** 백업 zip 을 [zipFile] 에 만든다. */
    suspend fun write(zipFile: File) = withContext(Dispatchers.IO) {
        val gson = Gson()
        val fanGoods = App.fanDatabase.fanGoodsDao().getAll()
        val officialGoods = App.database.goodsDao().getAll()
        val favoriteCharas = App.charaDatabase.charaDao().getAll().filter { it.charaIsFavorite }.map { it.charaNm }

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            fun putText(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            putText(FAN, gson.toJson(fanGoods))
            putText(OFFICIAL, gson.toJson(officialGoods.map { OfficialGoodsBackup.of(it) }))
            putText(FAVORITES, gson.toJson(favoriteCharas))
            // 같은 사진을 두 굿즈가 쓰면 같은 이름의 항목이 두 번 들어가 zip 만들기가 통째로 실패하므로 한 번만 넣는다.
            val written = mutableSetOf<String>()
            fanGoods.forEach { goods ->
                if (goods.fanGoodsImgPath.isEmpty()) return@forEach
                val imgFile = File(goods.fanGoodsImgPath)
                if (!imgFile.isFile || !written.add(imgFile.name)) return@forEach
                zip.putNextEntry(ZipEntry("$IMAGES/${imgFile.name}"))
                FileInputStream(imgFile).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * [input] 의 백업을 불러온다. 사진은 [filesDir] 에 푼다.
     * JSON 을 모두 읽고 확인한 뒤에야 DB 를 바꾸므로, 백업이 깨져 있으면 아무것도 바뀌지 않는다.
     * 공식 굿즈·2차창작·선호 캐릭터는 각각 한 트랜잭션으로 바꾼다.
     */
    suspend fun restore(
        input: InputStream,
        filesDir: File,
        onProgress: (Float) -> Unit = {}
    ): RestoreReport = withContext(Dispatchers.IO) {
        var fanJson: String? = null
        var officialJson: String? = null
        var favoriteJson: String? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // PC 에서 풀었다 다시 묶어 "wowa_backup/fan_goods.json" 처럼 폴더가 하나 더 붙어도 알아본다.
                val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                val name = parts.lastOrNull().orEmpty()
                when {
                    entry.isDirectory || name.isEmpty() -> Unit
                    parts.size >= 2 && parts[parts.size - 2] == IMAGES -> {
                        // 경로는 버리고 파일 이름만 써서, zip 안의 "../" 같은 경로로 앱 폴더 밖에 쓰지 않게 한다.
                        // 사진을 메모리에 모아 두지 않고 바로 파일로 써서, 사진이 많아도 메모리가 넘치지 않는다.
                        if (name != "." && name != "..") {
                            File(filesDir, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    name == FAN -> fanJson = zip.bufferedReader().readText()
                    name == OFFICIAL -> officialJson = zip.bufferedReader().readText()
                    name == FAVORITES -> favoriteJson = zip.bufferedReader().readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (fanJson == null && officialJson == null && favoriteJson == null) {
            throw IllegalStateException("백업 파일이 아닙니다")
        }
        onProgress(0.45f)

        // 바꾸기 전에 전부 읽어 둔다. 하나라도 깨져 있으면 여기서 멈춘다.
        // 빈 파일이나 "null" 은 빈 목록으로 보지 않는다. 공식 굿즈가 빈 목록이면 모든 보유 기록을 지우게 된다.
        val gson = Gson()
        // [null, {...}] 처럼 빈 항목이 섞여 있으면 그 항목만 버린다.
        fun <T : Any> parseList(name: String, json: String, type: java.lang.reflect.Type): List<T> =
            try {
                gson.fromJson<List<T?>?>(json, type)?.filterNotNull()
            } catch (e: Exception) {
                null
            } ?: throw IllegalStateException("백업 파일이 손상되었습니다 ($name)")
        val fanBackups: List<FanGoodsBackup>? =
            fanJson?.let { parseList(FAN, it, object : TypeToken<List<FanGoodsBackup>>() {}.type) }
        val officialBackups: List<OfficialGoodsBackup>? =
            officialJson?.let { parseList(OFFICIAL, it, object : TypeToken<List<OfficialGoodsBackup>>() {}.type) }
        val favorites: List<String>? =
            favoriteJson?.let { parseList(FAVORITES, it, object : TypeToken<List<String>>() {}.type) }

        // 굿즈 데이터를 아직 받지 않았으면 짝을 하나도 못 찾아 보유 기록이 모두 사라진다.
        if (officialBackups.orEmpty().any { it.isOwned } && App.database.goodsDao().count() == 0) {
            throw IllegalStateException("굿즈 데이터가 아직 없습니다. 마이페이지에서 수동 업데이트를 한 뒤 다시 불러오세요")
        }

        fanBackups?.let { backups ->
            val entities = backups.map { backup ->
                val oldPath = backup.fanGoodsImgPath.orEmpty()
                val newPath = if (oldPath.isNotEmpty()) File(filesDir, File(oldPath).name).absolutePath else ""
                backup.toEntity(newPath)
            }
            val dao = App.fanDatabase.fanGoodsDao()
            val oldImages = dao.getAll().map { it.fanGoodsImgPath }
            // 지우고 넣는 사이에 멈추면 2차창작 굿즈가 사라지므로 한 번에 처리한다.
            App.fanDatabase.withTransaction {
                dao.deleteAll()
                dao.insertAll(entities)
            }
            // 바뀌기 전 굿즈의 사진 중 이제 아무도 쓰지 않는 것은 지운다. 백업에서 푼 같은 이름의 사진은 새 굿즈가 쓰므로 남는다.
            dao.deleteUnusedImages(oldImages)
        }
        onProgress(0.6f)

        var ownedInBackup: Int? = null
        var restored = 0
        officialBackups?.let { backups ->
            // 상태를 비우고 다시 채우는 사이에 멈추면 보유 기록이 반만 남으므로 한 번에 처리한다.
            App.database.withTransaction {
                val dao = App.database.goodsDao()
                dao.resetAllGotten()
                val pairs = matchOfficialBackups(backups, dao.getAll())
                pairs.forEachIndexed { i, (backup, goods) ->
                    dao.update(backup.applyTo(goods))
                    onProgress(0.6f + 0.32f * (i + 1) / pairs.size)
                }
                ownedInBackup = backups.count { it.isOwned }
                restored = pairs.size
            }
        }
        onProgress(0.92f)

        favorites?.let { names ->
            val nameSet = names.map { it.trim() }.toSet()
            // 선호 표시가 반만 바뀐 채로 남지 않게 한 번에 처리한다.
            App.charaDatabase.withTransaction {
                val dao = App.charaDatabase.charaDao()
                dao.getAll().forEach { chara ->
                    val favorite = chara.charaNm.trim() in nameSet
                    if (chara.charaIsFavorite != favorite) dao.update(chara.copy(charaIsFavorite = favorite))
                }
            }
        }
        onProgress(1f)

        RestoreReport(
            ownedInBackup = ownedInBackup,
            restored = restored,
            fanGoods = fanBackups?.size,
            favorites = favorites?.size
        )
    }
}
