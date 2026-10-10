package com.example.wowagoodsproject

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.wowagoodsproject.component.encodeGoodsImagePath
import com.example.wowagoodsproject.db.news.NewsEntity
import com.example.wowagoodsproject.db.news.NewsType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 소식이 새로 쌓이면 알린다(오전 5시 업데이트, 수동 업데이트 모두).
 * - 선호 캐릭터 신규 굿즈
 * - 보유/구매예정 굿즈의 변경
 * 캐릭터마다 알림을 따로 띄우고, 알림 아이콘 자리에 캐릭터 얼굴을 넣는다. 누르면 소식 화면이 열린다.
 */
object NewsNotifier {

    // 채널 id 는 예전(신규 굿즈만 알리던) 것을 그대로 써서 사용자가 바꾼 채널 설정을 유지한다.
    private const val CHANNEL_ID = "new_goods"
    private const val NOTIFICATION_ID = 3_000_001

    /** 알림 하나에 펼쳐 보여 줄 최대 굿즈 줄 수 */
    private const val MAX_LINES = 5

    private const val ICON_SIZE_PX = 256
    private const val ICON_TIMEOUT_MS = 10_000L

    /** 방금 소식에 추가된 항목들로 알림을 띄운다. 반환값은 알림을 하나라도 띄웠는지. */
    suspend fun notifyNews(context: Context, news: List<NewsEntity>): Boolean {
        if (news.isEmpty() || !PendingGoodsNotifier.hasPermission(context)) return false
        ensureChannel(context)

        val charas = App.charaDatabase.charaDao().getAll()
        val favorites = charas.filter { it.charaIsFavorite }.map { it.charaNm }.toSet()
        val groups = LinkedHashMap<String, MutableList<NewsEntity>>()
        news.forEach { item ->
            ownersOf(item.newsChara, favorites).forEach { groups.getOrPut(it) { mutableListOf() }.add(item) }
        }

        val openNews = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            OpenTarget.intentFor(context, OpenTarget.News),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val manager = NotificationManagerCompat.from(context)
        val me = Person.Builder().setName("나").build()
        val shortcutIds = mutableListOf<String>()
        var shown = false

        groups.forEach { (name, items) ->
            val names = name.split(",").map { it.trim() }
            val imageUrl = charas.firstOrNull { it.charaNm in names && it.charaUrl.isNotEmpty() }?.charaUrl
            val icon = imageUrl?.let { loadIcon(context, it) }
            val person = Person.Builder().setName(name).setIcon(icon).build()

            // 알림 왼쪽 아이콘을 캐릭터 얼굴로 바꾸려면 대화 알림이어야 하고, 대화 알림에는 바로가기가 필요하다.
            val shortcutId = "news_chara_$name"
            val hasShortcut = try {
                ShortcutManagerCompat.pushDynamicShortcut(
                    context,
                    ShortcutInfoCompat.Builder(context, shortcutId)
                        .setShortLabel(name)
                        .setLongLived(true)
                        .setPerson(person)
                        .apply { if (icon != null) setIcon(icon) }
                        .setIntent(OpenTarget.intentFor(context, OpenTarget.News).setAction(Intent.ACTION_VIEW))
                        .build()
                )
            } catch (e: Exception) {
                false
            }
            if (hasShortcut) shortcutIds.add(shortcutId)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openNews)
                .setAutoCancel(true)
                .setStyle(
                    NotificationCompat.MessagingStyle(me)
                        .addMessage(messageOf(items), System.currentTimeMillis(), person)
                )
            if (hasShortcut) builder.setShortcutId(shortcutId)
            if (items.size > 1) builder.setNumber(items.size)

            try {
                // 같은 캐릭터 알림이 남아 있으면 새 것으로 바뀐다.
                manager.notify(name, NOTIFICATION_ID, builder.build())
                shown = true
            } catch (e: SecurityException) {
                // 알림 권한이 그사이 회수된 경우
            }
        }

        // 알림이 떠 있는 동안은 시스템이 바로가기를 따로 쥐고 있으므로,
        // 홈 화면에서 앱 아이콘을 길게 눌렀을 때 캐릭터 이름이 나오지 않게 바로 지운다.
        if (shortcutIds.isNotEmpty()) {
            try {
                ShortcutManagerCompat.removeDynamicShortcuts(context, shortcutIds)
            } catch (e: Exception) {
                // 못 지워도 알림에는 문제가 없다.
            }
        }
        return shown
    }

    /**
     * 이 굿즈를 어느 캐릭터 알림에 넣을지.
     * 캐릭터가 여럿인 굿즈("A, B")는 그중 선호 캐릭터 각각의 알림에 넣고,
     * 선호 캐릭터가 없으면(보유 굿즈 변경 등) 적힌 이름 그대로 한 알림으로 둔다.
     */
    private fun ownersOf(chara: String, favorites: Set<String>): List<String> {
        val names = chara.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return names.filter { it in favorites }.ifEmpty { listOf(chara.ifBlank { "굿즈" }) }
    }

    /**
     * 굿즈가 하나면 한 줄로 적는다.
     * 여러 개면 첫 줄은 접힌 알림에 보이는 요약, 그 아래는 펼쳤을 때 보이는 굿즈 목록이다.
     */
    private fun messageOf(items: List<NewsEntity>): String {
        items.singleOrNull()?.let { item ->
            val label = if (item.type == NewsType.NEW_GOODS) "신규 굿즈" else "굿즈 변경"
            return listOf(label, item.newsSeries, item.newsCategory).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val newCount = items.count { it.type == NewsType.NEW_GOODS }
        val changedCount = items.size - newCount
        val countText = when {
            changedCount == 0 -> "신규 굿즈 ${newCount}개"
            newCount == 0 -> "굿즈 변경 ${changedCount}개"
            else -> "신규 ${newCount}개 · 변경 ${changedCount}개"
        }
        val categories = items.map { it.newsCategory }.filter { it.isNotBlank() }.distinct()
        val summary = listOf(countText, categories.joinToString(", "))
            .filter { it.isNotEmpty() }.joinToString(" · ")

        val mixed = newCount > 0 && changedCount > 0
        val lines = items.take(MAX_LINES).map { item ->
            val tag = when {
                !mixed -> ""
                item.type == NewsType.NEW_GOODS -> "[신규] "
                else -> "[변경] "
            }
            tag + listOf(item.newsSeries, item.newsCategory).filter { it.isNotBlank() }.joinToString(" · ")
        }.filter { it.isNotBlank() }
        val more = if (items.size > MAX_LINES) listOf("외 ${items.size - MAX_LINES}개") else emptyList()
        return (listOf(summary) + lines + more).joinToString("\n")
    }

    /** 캐릭터 이미지를 동그랗게 잘라 알림 아이콘으로 만든다. 못 받으면 null(이름 첫 글자 아이콘으로 뜬다). */
    private suspend fun loadIcon(context: Context, url: String): IconCompat? = try {
        withTimeoutOrNull(ICON_TIMEOUT_MS) {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(encodeGoodsImagePath(url))
                    .size(ICON_SIZE_PX)
                    .allowHardware(false)
                    .transformations(CircleCropTransformation())
                    .build()
            )
            (result.drawable as? BitmapDrawable)?.bitmap?.let { IconCompat.createWithBitmap(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
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
