package com.instantstatusbutton.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.instantstatusbutton.app.auth.LinkedInPoster
import com.instantstatusbutton.app.auth.PostException
import com.instantstatusbutton.app.auth.XPoster
import com.instantstatusbutton.app.data.Network
import java.util.concurrent.TimeUnit

/** Posts one status to one network (X or LinkedIn) in the background and reports the outcome. */
class PostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val network = Network.valueOf(inputData.getString(KEY_NETWORK) ?: return Result.failure())
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        return try {
            when (network) {
                Network.X -> XPoster.post(applicationContext, text)
                Network.LINKEDIN -> LinkedInPoster.post(applicationContext, text)
                else -> return Result.failure()
            }
            Notifier.report(applicationContext, network.name, true, "Posted to ${network.title}")
            Result.success()
        } catch (e: PostException) {
            if (e.retryable && runAttemptCount < MAX_ATTEMPTS - 1) return Result.retry()
            Notifier.report(applicationContext, network.name, false, "${network.title}: ${e.message}")
            Result.failure()
        } catch (e: Exception) {
            Notifier.report(applicationContext, network.name, false, "${network.title}: ${e.message ?: e.javaClass.simpleName}")
            Result.failure()
        }
    }

    companion object {
        private const val KEY_NETWORK = "network"
        private const val KEY_TEXT = "text"
        private const val MAX_ATTEMPTS = 4

        fun enqueue(context: Context, network: Network, text: String) {
            val request = OneTimeWorkRequestBuilder<PostWorker>()
                .setInputData(Data.Builder().putString(KEY_NETWORK, network.name).putString(KEY_TEXT, text).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
