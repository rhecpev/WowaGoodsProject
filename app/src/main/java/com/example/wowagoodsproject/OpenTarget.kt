package com.example.wowagoodsproject

import android.content.Intent

/** 알림을 눌렀을 때 열 화면. 알림 인텐트에 실어 MainActivity 로 넘긴다. */
sealed interface OpenTarget {

    /** 굿즈 상세. 공식 굿즈면 해당 시리즈에서, 2차창작이면 2차창작 탭에서 연다. */
    data class Goods(val isFan: Boolean, val id: Int) : OpenTarget

    /** 소식 화면 */
    data object News : OpenTarget

    fun putInto(intent: Intent): Intent = when (this) {
        is Goods -> intent.putExtra(EXTRA_KIND, KIND_GOODS).putExtra(EXTRA_IS_FAN, isFan).putExtra(EXTRA_ID, id)
        News -> intent.putExtra(EXTRA_KIND, KIND_NEWS)
    }

    companion object {
        private const val EXTRA_KIND = "open_target_kind"
        private const val EXTRA_IS_FAN = "open_goods_is_fan"
        private const val EXTRA_ID = "open_goods_id"
        private const val KIND_GOODS = "goods"
        private const val KIND_NEWS = "news"

        /** 인텐트에서 꺼낸 뒤 지워, 화면을 돌려 액티비티가 다시 만들어져도 또 열리지 않게 한다. */
        fun takeFrom(intent: Intent?): OpenTarget? {
            val kind = intent?.getStringExtra(EXTRA_KIND) ?: return null
            val target = when (kind) {
                KIND_GOODS -> Goods(intent.getBooleanExtra(EXTRA_IS_FAN, false), intent.getIntExtra(EXTRA_ID, 0))
                KIND_NEWS -> News
                else -> null
            }
            intent.removeExtra(EXTRA_KIND)
            intent.removeExtra(EXTRA_IS_FAN)
            intent.removeExtra(EXTRA_ID)
            return target
        }

        /** 이 화면을 여는 알림용 인텐트. 이미 앱이 떠 있으면 새로 만들지 않고 onNewIntent 로 받는다. */
        fun intentFor(context: android.content.Context, target: OpenTarget): Intent =
            target.putInto(Intent(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
