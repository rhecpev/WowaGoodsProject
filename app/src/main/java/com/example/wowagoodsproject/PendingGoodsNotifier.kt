package com.example.wowagoodsproject

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 구매예정 굿즈 알림.
 * - 수령예정일이 지났는데 아직 구매예정인 굿즈: 지난 지 1, 4, 7, 10…일째(3일마다) 알린다.
 * - 배송을 시작한 지 일주일이 지났는데 아직 구매예정인 굿즈: 7, 10, 13…일째(3일마다) 알린다.
 * 매일 오전 5시에 [PendingGoodsWorker] 가 확인한다([DailySchedule]). 알림의 "이 굿즈 알림 받지 않기" 를 누르면
 * 그 굿즈의 그 알림만 더 이상 오지 않는다(날짜를 바꾸면 다시 온다).
 */
object PendingGoodsNotifier {

    private const val CHANNEL_ID = "receive_overdue"
    private const val PREFS = "receive_overdue"

    /** "알림키|회차" 목록. 회차는 지금까지 알린 가장 늦은 3일 묶음 번호. */
    private const val KEY_NOTIFIED_SLOTS = "notified_slots"

    /** 예전(한 번만 알리던) 기록. 쓰지 않으므로 지운다. */
    private const val LEGACY_KEY_NOTIFIED = "notified_keys"

    /** 사용자가 알림에서 끈 알림키 목록 */
    private const val KEY_MUTED = "muted_keys"

    /** 배송 시작 후 이 날수가 지나도 구매예정이면 알린다. */
    private const val SHIPPING_ALERT_DAYS = 7

    /** 처음 알린 뒤 이 날수마다 다시 알린다. */
    private const val REPEAT_DAYS = 3

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    internal const val ACTION_MUTE = "com.example.wowagoodsproject.action.MUTE_PENDING_ALERT"
    internal const val EXTRA_ALERT_KEY = "alert_key"
    internal const val EXTRA_NOTIFICATION_ID = "notification_id"

    private enum class Kind(val keyPrefix: String, val idOffset: Int) {
        RECEIVE("r", 0),
        SHIPPING("s", 2_000_000)
    }

    /** 공식/2차창작 id 가 겹쳐도 알림이 서로 덮지 않도록 2차창작은 이만큼 띄운다. */
    private const val FAN_ID_OFFSET = 1_000_000

    /**
     * @param slot 몇 번째 알림 차례인지. 같은 알림키에서 이 값이 커졌을 때만 다시 알린다.
     */
    private class Alert(val kind: Kind, val goods: GoodsItem, val slot: Int) {
        val target = goods.target()

        /** 굿즈·알림 종류·날짜가 같으면 같은 알림이다. 날짜가 바뀌면 새 알림으로 본다. */
        val key = "${kind.keyPrefix}:${if (target.isFan) "f" else "o"}:${target.id}:" +
                (if (kind == Kind.RECEIVE) goods.receiveDate else goods.shippingDate)

        val notificationId = kind.idOffset + (if (target.isFan) FAN_ID_OFFSET else 0) + target.id
    }

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    private fun dateString(daysAgo: Int = 0): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        return dateFormat().format(cal.time)
    }

    /** [date] 부터 오늘까지 며칠이 지났는지. 형식이 잘못됐으면 null. */
    private fun daysSince(date: String): Long? {
        val then = runCatching { dateFormat().parse(date) }.getOrNull() ?: return null
        val today = dateFormat().parse(dateString()) ?: return null
        // 서머타임이 있는 지역에서도 하루 단위로 맞도록 반올림한다.
        return ((today.time - then.time).toDouble() / DAY_MILLIS).roundToLong()
    }

    /** 구매예정 굿즈(세트 굿즈 제외) */
    private suspend fun pendingGoods(): List<GoodsItem> {
        val official = App.database.goodsDao().getAll()
            .filter { it.goodsCategory != CATEGORY_SET && it.status == GoodsStatus.PENDING }
        val fan = App.fanDatabase.fanGoodsDao().getAll().filter { it.status == GoodsStatus.PENDING }
        return official + fan
    }

    private suspend fun findAlerts(): List<Alert> = pendingGoods().flatMap { goods ->
        listOfNotNull(
            // 지난 지 1~3일째는 0회차, 4~6일째는 1회차 … 이므로 1, 4, 7, 10일째에 새 회차가 된다.
            goods.receiveDate.takeIf { it.isNotEmpty() }?.let(::daysSince)?.takeIf { it >= 1 }?.let { days ->
                Alert(Kind.RECEIVE, goods, slot = ((days - 1) / REPEAT_DAYS).toInt())
            },
            // 배송 시작 7~9일째는 0회차, 10~12일째는 1회차 … 이므로 7, 10, 13일째에 새 회차가 된다.
            goods.shippingDate.takeIf { it.isNotEmpty() }?.let(::daysSince)?.takeIf { it >= SHIPPING_ALERT_DAYS }?.let { days ->
                Alert(Kind.SHIPPING, goods, slot = ((days - SHIPPING_ALERT_DAYS) / REPEAT_DAYS).toInt())
            }
        )
    }

    /** 배송 시작 후 [SHIPPING_ALERT_DAYS]일이 지났는지. 목록에서 강조 표시할 때 쓴다. */
    fun isShippingOverdue(shippingDate: String): Boolean =
        shippingDate.isNotEmpty() && (daysSince(shippingDate) ?: 0) >= SHIPPING_ALERT_DAYS

    /** 이번 회차를 아직 알리지 않았고 꺼 두지 않은 것만 알린다. 반환값은 새로 띄운 알림 수. */
    suspend fun checkAndNotify(context: Context): Int {
        val alerts = findAlerts().associateBy { it.key }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val slots = prefs.getStringSet(KEY_NOTIFIED_SLOTS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val (key, slot) = entry.split("|").takeIf { it.size == 2 } ?: return@mapNotNull null
                slot.toIntOrNull()?.let { key to it }
            }
            .toMap()
        val muted = prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty()

        // 권한이 없어 못 띄운 알림은 기록하지 않아 다음 확인 때 다시 시도한다.
        val sent = alerts.values.filter { alert ->
            alert.key !in muted && alert.slot > (slots[alert.key] ?: -1) && notify(context, alert)
        }
        // 더 이상 해당하지 않는 기록은 지워, 날짜를 바꿨다가 다시 지나면 처음부터 알리게 한다.
        val newSlots = slots.filterKeys { it in alerts } + sent.associate { it.key to it.slot }
        prefs.edit()
            .putStringSet(KEY_NOTIFIED_SLOTS, newSlots.map { (key, slot) -> "$key|$slot" }.toSet())
            .putStringSet(KEY_MUTED, muted.filter { it in alerts }.toSet())
            .remove(LEGACY_KEY_NOTIFIED)
            .apply()
        return sent.size
    }

    /** 알림의 "이 굿즈 알림 받지 않기" 를 눌렀을 때 */
    internal fun mute(context: Context, key: String, notificationId: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val muted = prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_MUTED, muted + key).apply()
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "구매예정 굿즈 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "수령예정일이 지났거나 배송 시작 후 일주일이 지났는데 아직 구매예정인 굿즈를 알려 줍니다" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notify(context: Context, alert: Alert): Boolean {
        if (!hasPermission(context)) return false
        ensureChannel(context)
        val goods = alert.goods

        // 누르면 앱을 열고 해당 굿즈 위치로 이동한다.
        val openGoods = PendingIntent.getActivity(
            context,
            alert.notificationId, // 알림마다 다른 굿즈를 열어야 하므로 요청 코드를 나눈다.
            OpenTarget.intentFor(context, alert.target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val muteIntent = PendingIntent.getBroadcast(
            context,
            alert.notificationId,
            Intent(context, PendingAlertMuteReceiver::class.java)
                .setAction(ACTION_MUTE)
                .putExtra(EXTRA_ALERT_KEY, alert.key)
                .putExtra(EXTRA_NOTIFICATION_ID, alert.notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, detail) = when (alert.kind) {
            Kind.RECEIVE ->
                "${goods.displayName()} 굿즈가 수령예정일을 지났습니다." to "수령예정일 ${goods.receiveDate}"
            Kind.SHIPPING ->
                "${goods.displayName()} 굿즈가 배송 시작 후 일주일이 지났습니다." to "배송 시작 ${goods.shippingDate}"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(listOf(goods.series, detail).filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(openGoods)
            .addAction(0, "이 굿즈 알림 받지 않기", muteIntent)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(alert.notificationId, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun GoodsItem.displayName(): String =
        listOf(chara, category).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { series }

    private fun GoodsItem.target(): OpenTarget.Goods = when (this) {
        is FanGoodsEntity -> OpenTarget.Goods(isFan = true, id = fanGoodsId)
        is GoodsEntity -> OpenTarget.Goods(isFan = false, id = goodsId)
        else -> OpenTarget.Goods(isFan = false, id = 0)
    }
}

class PendingGoodsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PendingGoodsNotifier.checkAndNotify(applicationContext)
        return Result.success()
    }
}

/** 알림의 "이 굿즈 알림 받지 않기" 버튼을 받는다. */
class PendingAlertMuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PendingGoodsNotifier.ACTION_MUTE) return
        val key = intent.getStringExtra(PendingGoodsNotifier.EXTRA_ALERT_KEY) ?: return
        val notificationId = intent.getIntExtra(PendingGoodsNotifier.EXTRA_NOTIFICATION_ID, 0)
        PendingGoodsNotifier.mute(context, key, notificationId)
    }
}
