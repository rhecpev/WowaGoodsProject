package com.example.wowagoodsproject.db.betray

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.goodsStatusOf

/*
 * 배신 굿즈: 명조가 아닌 다른 장르의 굿즈를 장르별로 따로 모아 두는 앱 속 앱.
 * 받아 올 원본 데이터가 없어서 공식 굿즈도 2차창작처럼 직접 등록한다. 공식/2차창작은 [BetrayGoodsEntity.kind] 로만 나뉜다.
 * 캐릭터 목록은 등록한 굿즈의 캐릭터 칸에서 만들고, 사용자가 직접 추가한 캐릭터([BetrayCharaEntity], 이름·사진)를 더한다.
 * 선호 캐릭터는 [BetrayFavoriteEntity] 에 적어 둔다.
 * 명조 쪽 캐릭터(CharaDatabase)와는 전혀 섞이지 않는다.
 */

/** 장르(예: 원신, 블루아카). 장르마다 굿즈·선호 캐릭터가 따로다. */
@Entity(tableName = "tb_betray_genre")
data class BetrayGenreEntity(
    @PrimaryKey(autoGenerate = true)
    val genreId: Int = 0,
    val genreNm: String = "",
    /** 장르 목록 순서. 작을수록 위. */
    val genreOrder: Int = 0,
    /** 장르 배경 사진(직사각형, 그리드 보기). 앱 폴더 안 경로, 없으면 빈 문자열. DB 2부터. */
    val genreImgPath: String = "",
    /** 장르 아이콘(원 보기). 정사각형으로 잘라 둔 사진. 없으면 빈 문자열. DB 2부터. */
    val genreIconPath: String = ""
)

/** 굿즈 종류. 공식 탭과 2차창작 탭을 나눈다. */
enum class BetrayKind(val label: String) { OFFICIAL("공식"), FAN("2차창작") }

fun betrayKindOf(name: String?): BetrayKind = BetrayKind.entries.firstOrNull { it.name == name } ?: BetrayKind.FAN

@Entity(tableName = "tb_betray_goods", indices = [Index("genreId")])
data class BetrayGoodsEntity(
    @PrimaryKey(autoGenerate = true)
    val goodsId: Int = 0,
    val genreId: Int = 0,
    /** [BetrayKind] 이름 */
    val goodsKind: String = BetrayKind.OFFICIAL.name,
    /** 등록한 날("yyyy-MM-dd"). 목록은 최근 등록이 위다. */
    val goodsRegDate: String = "",
    val goodsSeries: String = "",
    val goodsPrice: String = "",
    val goodsChara: String = "",
    val goodsCategory: String = "",
    val goodsImgPath: String = "",
    val goodsStatus: String = GoodsStatus.NOT_GOTTEN.name,
    val goodsMemo: String = "",
    val goodsPurchaseDate: String = "",
    val goodsPurchaseStore: String = "",
    val goodsReceiveDate: String = "",
    val goodsQuantity: Int = 1,
    val goodsShippingDate: String = ""
) : GoodsItem {
    val kind get() = betrayKindOf(goodsKind)
    override val imgPath get() = goodsImgPath
    override val series get() = goodsSeries
    override val chara get() = goodsChara
    override val category get() = goodsCategory
    override val price get() = goodsPrice
    override val isGotten get() = goodsStatus == GoodsStatus.GOTTEN.name
    override val status get() = goodsStatusOf(goodsStatus)
    override val memo get() = goodsMemo
    override val purchaseDate get() = goodsPurchaseDate
    override val purchaseStore get() = goodsPurchaseStore
    override val receiveDate get() = goodsReceiveDate
    override val quantity get() = goodsQuantity
    override val shippingDate get() = goodsShippingDate
}

/** 상태를 바꾼다. 규칙은 2차창작(withStatus)과 같다: 미보유면 수량 1, 구매예정이 아니면 배송 시작일을 지운다. */
fun BetrayGoodsEntity.withStatus(status: GoodsStatus) = copy(
    goodsStatus = status.name,
    goodsQuantity = if (status == GoodsStatus.NOT_GOTTEN) 1 else goodsQuantity,
    goodsShippingDate = if (status == GoodsStatus.PENDING) goodsShippingDate else ""
)

fun BetrayGoodsEntity.withPurchaseInfo(info: PurchaseInfo) = copy(
    goodsPurchaseStore = info.purchaseStore,
    goodsPurchaseDate = info.purchaseDate,
    goodsReceiveDate = info.receiveDate
)

/** 장르별 선호 캐릭터. 이름 순서가 아니라 [favoriteOrder] 가 우선순위다(작을수록 앞). */
@Entity(tableName = "tb_betray_favorite", primaryKeys = ["genreId", "charaNm"])
data class BetrayFavoriteEntity(
    val genreId: Int,
    val charaNm: String,
    val favoriteOrder: Int = 0
)

/**
 * 사용자가 직접 추가한 배신 굿즈 캐릭터(장르별). 굿즈가 없어도 캐릭터별 탭에 나온다.
 * 굿즈와는 이름([charaNm])으로 이어진다. DB 3부터.
 */
@Entity(tableName = "tb_betray_chara", primaryKeys = ["genreId", "charaNm"])
data class BetrayCharaEntity(
    val genreId: Int,
    val charaNm: String,
    /** 캐릭터 사진(원으로 잘라 둔 정사각형). 없으면 빈 문자열 */
    val charaImgPath: String = ""
)

/**
 * 사용자가 등록한 배신 굿즈 시리즈(장르별): 이름·배경·아이콘. 굿즈와는 시리즈 이름(앞뒤 공백 무시)으로 이어진다.
 * 등록하지 않은 시리즈(굿즈에만 적힌 이름)도 굿즈 탭에 나온다. DB 4부터.
 */
@Entity(tableName = "tb_betray_series", primaryKeys = ["genreId", "seriesNm"])
data class BetraySeriesEntity(
    val genreId: Int,
    val seriesNm: String,
    val seriesImgPath: String = "",
    val seriesIconPath: String = "",
    /** 시리즈 날짜 "yyyy-MM-dd"(만들 때 입력, 기본은 만든 날). 비어 있으면 굿즈 중 가장 최근 등록일을 보인다. DB 5부터 */
    val seriesDate: String = ""
)

/** 굿즈 캐릭터 칸("A,B")의 이름들 */
fun charaNames(field: String): List<String> = field.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
