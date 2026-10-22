package com.example.wowagoodsproject.db.changed

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 보유 중이거나 구매 예정이던 굿즈가 데이터 업데이트로 바뀌었을 때의 기록. 마이페이지의 변경된 굿즈에 보여 준다.
 *
 * - 정보 변경([statusKept]): 굿즈 번호가 같아 가격·메모·이미지 등만 고쳤고, 상태는 그대로다.
 * - 대체·삭제: 굿즈가 원본에서 사라져 상태도 함께 사라졌다. 사라지기 직전의 정보를 남겨 두고,
 *   대체 항목이 있으면 그 굿즈에 상태를 되돌릴 수 있다.
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

    // 사라지기 직전에 사용자가 적어 둔 값. 상태를 되돌릴 때 함께 되돌린다.
    val oldQuantity: Int = 1,
    val oldPurchaseStore: String = "",
    val oldPurchaseDate: String = "",
    val oldReceiveDate: String = "",
    val oldShippingDate: String = "",

    /** 사용자가 상태를 되돌렸는지 */
    val isRestored: Boolean = false,

    /** 같은 굿즈의 정보만 바뀌어 상태가 그대로 남았는지. 되돌릴 것이 없다. */
    val statusKept: Boolean = false,
    /** 바뀐 뒤 굿즈의 번호(goodsUid). 0 이면 내용(new* 칸)으로 찾는다. */
    val newUid: Int = 0,
    /** 변경된 굿즈 화면에서 다이얼로그를 열어 이 굿즈의 상태·수량 등을 고쳤는지(확인 표시) */
    val isChecked: Boolean = false
) {
    /** 대체 항목이 있어야 상태를 되돌릴 수 있다. */
    val canRestore: Boolean get() = !isRestored && !statusKept && newSeries.isNotEmpty()

    /** 원본에서 완전히 사라져 옮겨 갈 굿즈가 없는 기록 */
    val isDeleted: Boolean get() = newSeries.isEmpty()

    val detailLines: List<String> get() = changedDetail.split("\n").filter { it.isNotBlank() }
}
