package com.example.wowagoodsproject.db.news

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NewsType {
    /** 선호 캐릭터가 포함된 굿즈가 새로 추가됨 */
    NEW_GOODS,

    /** 보유 중이거나 구매 예정인 굿즈에 변경이 생김 */
    CHANGED_GOODS
}

@Entity(tableName = "tb_news")
data class NewsEntity(
    @PrimaryKey(autoGenerate = true)
    val newsId: Int = 0,
    val newsType: String = NewsType.NEW_GOODS.name,
    val newsTime: Long = 0L,
    val newsSeries: String = "",
    val newsChara: String = "",
    val newsCategory: String = "",
    val newsPrice: String = "",
    val newsImgUrl: String = "",
    /** 변경 내용. 여러 줄이면 개행으로 구분한다. NEW_GOODS 는 비어 있다. */
    val newsDetail: String = "",
    /** 연결된 tb_changed_goods 기록 id. 없으면 0 */
    val newsChangedId: Int = 0,
    val newsIsRead: Boolean = false
) {
    val type: NewsType get() = runCatching { NewsType.valueOf(newsType) }.getOrDefault(NewsType.NEW_GOODS)
    val detailLines: List<String> get() = newsDetail.split("\n").filter { it.isNotBlank() }
}
