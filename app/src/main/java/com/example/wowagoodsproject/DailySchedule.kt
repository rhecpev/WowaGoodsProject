package com.example.wowagoodsproject

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 매일 오전 5시에 도는 백그라운드 작업.
 * - 데이터 업데이트: 새 소식이 쌓이면 소식 알림이 함께 간다([NewsNotifier]).
 * - 구매예정 굿즈 확인: 수령예정일/배송 알림([PendingGoodsNotifier]).
 * 안드로이드 절전 정책 때문에 정확히 5시가 아니라 조금 늦게 돌 수 있다.
 */
object DailySchedule {

    private const val RUN_HOUR = 5

    private const val UPDATE_WORK_NAME = "daily_data_update_5am"
    private const val PENDING_WORK_NAME = "daily_pending_check_5am"

    /** 예전에 쓰던 작업 이름들. 시각이 5시가 아니거나 지워진 워커를 가리키므로 취소한다. */
    private val LEGACY_WORK_NAMES = listOf("receive_overdue_check", "pending_goods_check")

    /** 앱 시작 때 부른다. 이미 걸려 있으면 그대로 두어 5시 주기가 흐트러지지 않게 한다. */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        LEGACY_WORK_NAMES.forEach { workManager.cancelUniqueWork(it) }

        val delay = millisUntilNextRun()
        workManager.enqueueUniquePeriodicWork(
            UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScheduledUpdateWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                // 5시에 인터넷이 안 되면 연결될 때 돈다.
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            PENDING_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PendingGoodsWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    /** 다음 오전 5시까지 남은 시간 */
    private fun millisUntilNextRun(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, RUN_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}

/**
 * 오전 5시 데이터 업데이트. 화면에 아무것도 띄우지 않는다.
 * 결과는 마이페이지 '마지막 업데이트' 에 남고, 새 소식이 있으면 소식 알림만 간다.
 */
class ScheduledUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        UpdateManager.runFullUpdate(showProgress = false)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.d("ScheduledUpdateWorker", "업데이트 실패: ${e.message}")
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
