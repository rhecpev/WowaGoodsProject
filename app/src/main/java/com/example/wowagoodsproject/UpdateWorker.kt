package com.example.wowagoodsproject

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("UpdateWorker", "Worker 시작")
            val total = UpdateManager.runFullUpdate()
            android.util.Log.d("UpdateWorker", "Worker 완료")

            val msg = if (total > 0) "데이터 업데이트 완료! ${total}개 항목 변경됨"
            else "데이터가 최신 상태입니다"
            showToast(msg)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.d("UpdateWorker", "Worker 에러: ${e.message}")
            if (runAttemptCount < 5) {
                showToast("데이터 업데이트 실패 - 잠시 후 다시 시도합니다")
                Result.retry()
            } else {
                android.util.Log.d("UpdateWorker", "5회 재시도 초과, 포기")
                showToast("데이터 업데이트에 실패했습니다")
                Result.failure()
            }
        }
    }

    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
