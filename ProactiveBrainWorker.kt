package com.faitapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProactiveBrainWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveBrainWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Proactive brain worker running")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in proactive brain worker: ${e.message}", e)
            Result.retry()
        }
    }
}
