package com.example.wowagoodsproject

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("UpdateWorker", "Worker 시작")
            val charaResult = UpdateManager.updateCharacters()
            val seriesResult = UpdateManager.updateSeries()
            val goodsResult = UpdateManager.updateGoods()
            android.util.Log.d("UpdateWorker", "Worker 완료")

            val total = charaResult.first + charaResult.second + charaResult.third +
                    seriesResult.first + seriesResult.second + seriesResult.third +
                    goodsResult.first + goodsResult.second + goodsResult.third

            val msg = if (total > 0) "데이터 업데이트 완료! ${total}개 항목 변경됨"
            else "데이터가 최신 상태입니다"

            val channelId = "update_channel"
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "데이터 업데이트",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("WowaGoods 업데이트")
                .setContentText(msg)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.d("UpdateWorker", "Worker 에러: ${e.message}")
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                android.util.Log.d("UpdateWorker", "5회 재시도 초과, 포기")
                Result.failure()
            }
        }
    }
}