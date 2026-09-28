package com.example.wowagoodsproject

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.wowagoodsproject.db.news.NewsEntity
import com.example.wowagoodsproject.db.news.NewsType

/**
 * 소식이 새로 쌓이면 알린다(오전 5시 업데이트, 수동 업데이트 모두).
 * - 선호 캐릭터 신규 굿즈
 * - 보유/구매예정 굿즈의 변경
 * 한 번에 여러 개가 들어오면 알림 하나로 묶는다. 누르면 소식 화면이 열린다.
 */
object NewsNotifier {

    // 채널 id 는 예전(신규 굿즈만 알리던) 것을 그대로 써서 사용자가 바꾼 채널 설정을 유지한다.
    private const val CHANNEL_ID = "new_goods"
    private const val NOTIFICATION_ID = 3_000_001

    /** 묶음 알림에 펼쳐 보여 줄 최대 줄 수 */
    private const val MAX_LINES = 5

    /** 방금 소식에 추가된 항목들로 알림을 띄운다. 반환값은 알림을 띄웠는지. */
    fun notifyNews(context: Context, news: List<NewsEntity>): Boolean {
        if (news.isEmpty() || !PendingGoodsNotifier.hasPermission(context)) return false
        ensureChannel(context)

        val openNews = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            OpenTarget.intentFor(context, OpenTarget.News),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openNews)
            .setAutoCancel(true)

        val newCount = news.count { it.type == NewsType.NEW_GOODS }
        val changedCount = news.size - newCount

        if (news.size == 1) {
            val item = news.first()
            builder
                .setContentTitle(
                    if (item.type == NewsType.NEW_GOODS) "${item.newsChara} 신규 굿즈가 추가되었습니다."
                    else "${item.newsChara} 굿즈에 변경 사항이 있습니다."
                )
                .setContentText(listOf(item.newsSeries, item.newsCategory).filter { it.isNotBlank() }.joinToString(" · "))
        } else {
            val lines = news.map { item ->
                val tag = if (item.type == NewsType.NEW_GOODS) "[신규]" else "[변경]"
                "$tag " + listOf(item.newsChara, item.newsCategory).filter { it.isNotBlank() }.joinToString(" · ")
            }
            val style = NotificationCompat.InboxStyle()
            lines.take(MAX_LINES).forEach { style.addLine(it) }
            if (lines.size > MAX_LINES) style.setSummaryText("외 ${lines.size - MAX_LINES}개")
            val title = when {
                changedCount == 0 -> "선호 캐릭터 신규 굿즈 ${newCount}개가 추가되었습니다."
                newCount == 0 -> "굿즈 변경 사항 ${changedCount}개가 있습니다."
                else -> "새 소식 ${news.size}개 (신규 ${newCount} · 변경 ${changedCount})"
            }
            builder
                .setContentTitle(title)
                .setContentText(lines.take(2).joinToString(", "))
                .setStyle(style)
                .setNumber(news.size)
        }

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "소식 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "선호 캐릭터 신규 굿즈나 보유·구매예정 굿즈의 변경이 소식에 추가되면 알려 줍니다" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
