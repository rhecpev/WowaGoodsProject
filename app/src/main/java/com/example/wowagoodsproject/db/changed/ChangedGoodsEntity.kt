package com.example.wowagoodsproject.db.changed

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 보유 중이거나 구매 예정이던 굿즈가 데이터 업데이트로 사라졌을 때의 보관 기록.
 *
 * 굿즈의 고유 키가 `시리즈|캐릭터|카테고리|가격|메모` 라서 가격이나 메모가 바뀌면
 * 수정이 아니라 삭제 + 추가로 처리된다. 그 과정에서 사용자가 직접 표시해 둔
 * 보유/구매예정 상태가 함께 사라지므로, 사라지기 직전의 정보를 여기에 남겨 둔다.
 */
@Entity(tableName = "tb_changed_goods")
data class ChangedGoodsEntity(
    @PrimaryKey(autoGenerate = true)
    val changedId: Int = 0,
    val changedTime: Long = 0L,

    // 사라진 굿즈의 원래 정보
    val oldSeries: String = "",
    val oldChara: String = "",
    val oldCategory: String = "",
    val oldPrice: String = "",
    val oldMemo: String = "",
    val oldUrl: String = "",
    val oldStatus: String = "",

    // 대체된 굿즈의 키. 대체 항목을 못 찾았으면(완전 삭제) 모두 빈 값이다.
    val newSeries: String = "",
    val newChara: String = "",
    val newCategory: String = "",
    val newPrice: String = "",
    val newMemo: String = "",
    val newUrl: String = "",

    /** 무엇이 바뀌었는지. 여러 줄이면 개행으로 구분한다. */
    val changedDetail: String = "",

    /** 사용자가 상태를 되돌렸는지 */
    val isRestored: Boolean = false
) {
    /** 대체 항목이 있어야 상태를 되돌릴 수 있다. */
    val canRestore: Boolean get() = !isRestored && newSeries.isNotEmpty()

    val detailLines: List<String> get() = changedDetail.split("\n").filter { it.isNotBlank() }
}
