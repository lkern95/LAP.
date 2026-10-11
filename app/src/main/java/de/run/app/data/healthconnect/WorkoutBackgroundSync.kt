package de.run.app.data.healthconnect

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.run.app.RunApplication
import de.run.app.domain.model.WorkoutReadResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class WorkoutBackgroundSyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<WorkoutBackgroundSyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    private companion object {
        const val WORK_NAME = "run-workout-background-sync"
        const val SYNC_INTERVAL_HOURS = 6L
    }
}

class WorkoutBackgroundSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as RunApplication).container.workoutRepository
        val end = Instant.now()
        return try {
            when (repository.refresh(end.minus(Duration.ofDays(90)), end, readInBackground = true)) {
                is WorkoutReadResult.Available,
                WorkoutReadResult.PermissionNeeded,
                WorkoutReadResult.Unavailable -> Result.success()
                WorkoutReadResult.Failed -> Result.retry()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
