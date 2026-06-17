package com.example.wowagoodsproject

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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

            // 슬롯 계산
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val slot = when {
                hour < 6 -> 0
                hour < 12 -> 1
                hour < 18 -> 2
                else -> 3
            }

            val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val statusStr = if (total > 0) "${total}개 변경됨" else "최신상태"

            val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
            val lastDate = prefs.getString("update_slot_date", "")

            prefs.edit().apply {
                // 날짜 바뀌면 슬롯 초기화
                if (lastDate != today) {
                    for (i in 0..3) {
                        putString("update_slot_${i}_time", "")
                        putString("update_slot_${i}_status", "")
                    }
                    putString("update_slot_date", today)
                }
                putString("update_slot_${slot}_time", timeStr)
                putString("update_slot_${slot}_status", statusStr)
            }.apply()

            withContext(Dispatchers.Main) {
                val msg = if (total > 0) "데이터 업데이트 완료! ${total}개 항목 변경됨"
                else "데이터가 최신 상태입니다"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }

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