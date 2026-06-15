package com.example.wowagoodsproject

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            withContext(Dispatchers.Main) {
                val msg = if (total > 0) "데이터 업데이트 완료! ${total}개 항목 변경됨"
                else "데이터가 최신 상태입니다"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.d("UpdateWorker", "Worker 에러: ${e.message}")
            android.util.Log.d("UpdateWorker", "에러 상세: ${e.stackTraceToString()}")
            Result.retry()
        }
    }
}