package com.example.wowagoodsproject

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 매일 오전 5시에 도는 백그라운드 작업.
 * - 데이터 업데이트: 새 소식이 쌓이면 소식 알림이 함께 간다([NewsNotifier]).
 *   5시에 못 돌았거나 실패했으면 성공할 때까지 매시 정각(6시, 7시, …)에 다시 돈다.
 * - 구매예정 굿즈 확인: 수령예정일/배송 알림([PendingGoodsNotifier]). 하루에 한 번만 돈다.
 *
 * 주기 작업(24시간 간격)은 한 번 늦게 돌면 그 시각이 새 기준이 되어 5시에서 계속 밀린다.
 * 그래서 매번 다음에 돌 시각에 정확 알람을 새로 걸고, 알람이 울리면 작업을 바로 돌린다.
 */
object DailySchedule {

    private const val RUN_HOUR = 5

    const val ACTION_RUN = "com.example.wowagoodsproject.action.DAILY_RUN"

    private const val UPDATE_WORK_NAME = "daily_data_update"
    private const val PENDING_WORK_NAME = "daily_pending_check"

    private const val PREFS_NAME = "wowa_prefs"
    private const val KEY_PENDING_CHECK_AT = "daily_pending_check_at"

    /** 예전에 쓰던 작업 이름들. 시각이 5시에서 밀리는 주기 작업이거나 지워진 워커를 가리키므로 취소한다. */
    private val LEGACY_WORK_NAMES = listOf(
        "receive_overdue_check",
        "pending_goods_check",
        "daily_data_update_5am",
        "daily_pending_check_5am"
    )

    /**
     * 다음에 돌 시각에 알람을 건다. 같은 알람을 다시 걸면 덮어쓰므로 몇 번을 불러도 된다.
     * - 오늘 5시 업데이트가 이미 성공했으면 다음 오전 5시.
     * - 아직 성공하지 못했으면 다음 정각(1시간 주기 재시도).
     * 앱 시작, 알람이 울린 직후, 업데이트 성공 직후, 재부팅/앱 업데이트/시각·시간대 변경 때 부른다.
     */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        LEGACY_WORK_NAMES.forEach { workManager.cancelUniqueWork(it) }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DailyAlarmReceiver::class.java).setAction(ACTION_RUN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = if (isUpdateDue(context)) nextHourMillis() else nextRunMillis()

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                // 절전(Doze) 중에도 정각에 기기를 깨운다.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // 정확 알람 권한이 그사이 회수된 경우. 조금 늦더라도 돌게 한다.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** 알람이 울렸을 때 부른다. 오늘 몫을 아직 못 한 작업만 지금 바로 돌린다. */
    fun runDue(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (isUpdateDue(context)) {
            // 지난 시각에 건 작업이 아직 인터넷을 기다리고 있으면 새로 걸어 다시 시도한다.
            workManager.enqueueUniqueWork(
                UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ScheduledUpdateWorker>()
                    // 인터넷이 안 되면 연결될 때 돈다.
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .expeditedIfSupported()
                    .build()
            )
        }

        // 구매예정 확인은 재시도 알람마다 알림이 반복되지 않도록 하루에 한 번만 돌린다.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRun = lastRunMillis()
        if (prefs.getLong(KEY_PENDING_CHECK_AT, 0L) < lastRun) {
            prefs.edit().putLong(KEY_PENDING_CHECK_AT, lastRun).apply()
            workManager.enqueueUniqueWork(
                PENDING_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PendingGoodsWorker>()
                    .expeditedIfSupported()
                    .build()
            )
        }
    }

    /**
     * 가장 최근 오전 5시 이후로 업데이트가 한 번도 성공하지 못했는지.
     * 수동 업데이트로 성공한 것도 성공으로 친다.
     */
    private fun isUpdateDue(context: Context): Boolean {
        val lastUpdate = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("last_update_time", null)
        val lastUpdateMillis = try {
            lastUpdate?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(it)?.time } ?: 0L
        } catch (e: Exception) {
            0L
        }
        return lastUpdateMillis < lastRunMillis()
    }

    /**
     * 절전 중에도 미루지 않고(인터넷 포함) 바로 돌게 한다.
     * 안드로이드 12 미만에서는 긴급 작업에 포그라운드 알림이 필요하므로 일반 작업으로 둔다.
     */
    private fun OneTimeWorkRequest.Builder.expeditedIfSupported(): OneTimeWorkRequest.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        } else this

    /** 오늘 오전 5시 정각(기기 시간대 기준) */
    private fun todayRunTime(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, RUN_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /** 가장 최근에 지난 오전 5시 */
    private fun lastRunMillis(): Long {
        val run = todayRunTime()
        if (run.timeInMillis > System.currentTimeMillis()) run.add(Calendar.DAY_OF_YEAR, -1)
        return run.timeInMillis
    }

    /** 다음 오전 5시 */
    private fun nextRunMillis(): Long {
        val run = todayRunTime()
        if (run.timeInMillis <= System.currentTimeMillis()) run.add(Calendar.DAY_OF_YEAR, 1)
        return run.timeInMillis
    }

    /** 다음 정각 */
    private fun nextHourMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.HOUR_OF_DAY, 1)
    }.timeInMillis
}

/**
 * 알람을 받아 작업을 돌리고 다음 알람을 다시 건다.
 * 재부팅·앱 업데이트 때는 알람이 지워지고, 시각·시간대가 바뀌면 5시가 어긋나므로 그때도 다시 건다.
 */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DailySchedule.ACTION_RUN) DailySchedule.runDue(context)
        DailySchedule.schedule(context)
    }
}

/**
 * 오전 5시 데이터 업데이트. 화면에 아무것도 띄우지 않는다.
 * 결과는 마이페이지 '마지막 업데이트' 에 남고, 새 소식이 있으면 소식 알림만 간다.
 * 실패하면 여기서 재시도하지 않는다. 다음 정각 알람이 다시 돌린다([DailySchedule]).
 */
class ScheduledUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        UpdateManager.runFullUpdate(showProgress = false)
        // 성공했으니 1시간 재시도를 끝내고 알람을 다음 오전 5시로 돌려놓는다.
        DailySchedule.schedule(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.d("ScheduledUpdateWorker", "업데이트 실패: ${e.message}")
        Result.failure()
    }
}
