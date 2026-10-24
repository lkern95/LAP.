package de.run.app.data.garmin

import android.content.Context
import de.run.app.data.fit.AndroidGarminFitImportRepository
import de.run.app.data.fit.GarminFitImportResult
import de.run.app.data.local.RunDatabase
import de.run.app.domain.repository.GarminConnectLoginResult
import de.run.app.domain.repository.GarminConnectRepository
import de.run.app.domain.repository.GarminConnectHistorySyncResult
import de.run.app.domain.repository.GarminConnectHistorySyncSummary
import de.run.app.domain.repository.GarminConnectSyncResult
import de.run.app.data.local.GarminHealthDayEntity
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AndroidGarminConnectRepository(
    context: Context,
    database: RunDatabase
) : GarminConnectRepository {
    private val tokenStore = AndroidGarminSessionStore(context)
    private val transport = GarminHttpTransport()
    private val auth = GarminConnectAuthClient(transport)
    private val activities = GarminConnectActivityClient(transport)
    private val health = GarminConnectHealthClient(transport)
    private val fitImporter = AndroidGarminFitImportRepository(database, context.filesDir)
    private val healthDays = database.garminHealthDayDao()

    override suspend fun connect(email: String, password: CharArray): GarminConnectLoginResult = withContext(Dispatchers.IO) {
        when (val result = auth.login(email, password)) {
            is GarminAuthResult.Authenticated -> {
                tokenStore.save(result.session)
                GarminConnectLoginResult.Connected
            }
            GarminAuthResult.NeedsMfa -> GarminConnectLoginResult.NeedsMfa
            GarminAuthResult.InvalidCredentials -> GarminConnectLoginResult.InvalidCredentials
            GarminAuthResult.AccessBlocked -> GarminConnectLoginResult.AccessBlocked
            GarminAuthResult.Failed -> GarminConnectLoginResult.Failed
        }
    }

    override suspend fun submitMfaCode(code: CharArray): GarminConnectLoginResult = withContext(Dispatchers.IO) {
        when (val result = auth.verifyMfa(code)) {
            is GarminAuthResult.Authenticated -> {
                tokenStore.save(result.session)
                GarminConnectLoginResult.Connected
            }
            GarminAuthResult.NeedsMfa -> GarminConnectLoginResult.NeedsMfa
            GarminAuthResult.InvalidCredentials -> GarminConnectLoginResult.InvalidCredentials
            GarminAuthResult.AccessBlocked -> GarminConnectLoginResult.AccessBlocked
            GarminAuthResult.Failed -> GarminConnectLoginResult.Failed
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        tokenStore.load() != null
    }

    override suspend fun syncActivityNear(target: LocalDateTime): GarminConnectSyncResult = withContext(Dispatchers.IO) {
        val stored = tokenStore.load() ?: return@withContext GarminConnectSyncResult.AuthenticationExpired
        val session = auth.refreshIfNeeded(stored) ?: run {
            tokenStore.clear()
            return@withContext GarminConnectSyncResult.AuthenticationExpired
        }
        if (session != stored) tokenStore.save(session)
        try {
            val activity = activities.findActivityNear(session, target)
                ?: return@withContext GarminConnectSyncResult.NoMatchingActivity
            val original = activities.downloadOriginal(session, activity.activityId)
            try {
                when (val saved = fitImporter.import(activity.activityId, original)) {
                    is GarminFitImportResult.Imported -> GarminConnectSyncResult.Imported(saved.workout, saved.sha256)
                    is GarminFitImportResult.AlreadyImported -> GarminConnectSyncResult.AlreadyImported(saved.workout)
                    GarminFitImportResult.InvalidFile, GarminFitImportResult.Conflict -> GarminConnectSyncResult.Failed
                    GarminFitImportResult.Failed -> GarminConnectSyncResult.Failed
                }
            } finally {
                original.fill(0)
            }
        } catch (error: GarminApiException) {
            when (error.statusCode) {
                HTTP_UNAUTHORIZED -> refreshAndRetry(target, session)
                HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> GarminConnectSyncResult.AccessBlocked
                else -> GarminConnectSyncResult.Failed
            }
        } catch (_: Exception) {
            GarminConnectSyncResult.Failed
        }
    }

    override suspend fun syncAllActivities(): GarminConnectHistorySyncResult = withContext(Dispatchers.IO) {
        val stored = tokenStore.load() ?: return@withContext GarminConnectHistorySyncResult.AuthenticationExpired
        val session = auth.refreshIfNeeded(stored) ?: run {
            tokenStore.clear()
            return@withContext GarminConnectHistorySyncResult.AuthenticationExpired
        }
        if (session != stored) tokenStore.save(session)

        val activities = try {
            this@AndroidGarminConnectRepository.activities.listAllActivities(session)
        } catch (error: GarminApiException) {
            return@withContext when (error.statusCode) {
                HTTP_UNAUTHORIZED -> GarminConnectHistorySyncResult.AuthenticationExpired
                HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> GarminConnectHistorySyncResult.Partial(
                    GarminConnectHistorySyncSummary(false, 0, 0, 0, 0, 0, error.statusCode == HTTP_TOO_MANY_REQUESTS,
                        error.statusCode == HTTP_FORBIDDEN, System.currentTimeMillis()),
                    authenticationExpired = false
                )
                else -> GarminConnectHistorySyncResult.Failed
            }
        } catch (_: Exception) {
            return@withContext GarminConnectHistorySyncResult.Failed
        }

        var imported = 0
        var alreadyPresent = 0
        var failed = 0
        var originalsSaved = 0
        var rateLimited = false
        var accessDenied = false
        var authenticationExpired = false
        for (activity in activities) {
            delay(DOWNLOAD_PACING_MILLIS)
            try {
                val original = this@AndroidGarminConnectRepository.activities.downloadOriginal(session, activity.activityId)
                try {
                    when (fitImporter.import(activity.activityId, original)) {
                        is GarminFitImportResult.Imported -> { imported += 1; originalsSaved += 1 }
                        is GarminFitImportResult.AlreadyImported -> { alreadyPresent += 1; originalsSaved += 1 }
                        else -> failed += 1
                    }
                } finally {
                    original.fill(0)
                }
            } catch (error: GarminApiException) {
                when (error.statusCode) {
                    HTTP_UNAUTHORIZED -> authenticationExpired = true
                    HTTP_FORBIDDEN -> accessDenied = true
                    HTTP_TOO_MANY_REQUESTS -> rateLimited = true
                    else -> {
                        failed += 1
                        continue
                    }
                }
                if (authenticationExpired || accessDenied || rateLimited) break
                failed += 1
            } catch (_: Exception) {
                failed += 1
            }
        }
        var healthDaysUpdated = 0
        var healthSyncFailed = false
        var healthSyncBlocked = false
        var healthAuthenticationExpired = false
        try {
            val fetchedHealth = health.readRecent(session, java.time.LocalDate.now())
            val mergedHealth = fetchedHealth.map { fresh ->
                val previous = healthDays.byDate(fresh.date.toString())
                GarminHealthDayEntity(
                    date = fresh.date.toString(),
                    sleepMinutes = fresh.sleepMinutes ?: previous?.sleepMinutes,
                    restingHeartRateBpm = fresh.restingHeartRateBpm ?: previous?.restingHeartRateBpm,
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
            healthDays.saveAll(mergedHealth)
            healthDaysUpdated = mergedHealth.count { it.sleepMinutes != null || it.restingHeartRateBpm != null }
        } catch (error: GarminApiException) {
            healthAuthenticationExpired = error.statusCode == HTTP_UNAUTHORIZED
            healthSyncBlocked = error.statusCode == HTTP_FORBIDDEN || error.statusCode == HTTP_TOO_MANY_REQUESTS
            healthSyncFailed = !healthAuthenticationExpired && !healthSyncBlocked
        } catch (_: Exception) {
            healthSyncFailed = true
        }

        val summary = GarminConnectHistorySyncSummary(
            listingCompleted = true,
            found = activities.size,
            newlyImported = imported,
            alreadyPresent = alreadyPresent,
            failed = failed,
            originalsSaved = originalsSaved,
            stoppedByRateLimit = rateLimited,
            stoppedByGarminAccess = accessDenied,
            completedAtMillis = System.currentTimeMillis(),
            healthDaysUpdated = healthDaysUpdated,
            healthSyncFailed = healthSyncFailed,
            healthSyncBlocked = healthSyncBlocked,
            healthAuthenticationExpired = healthAuthenticationExpired
        )
        val partial = rateLimited || accessDenied || authenticationExpired || failed > 0 ||
            healthSyncFailed || healthSyncBlocked || healthAuthenticationExpired
        if (partial) GarminConnectHistorySyncResult.Partial(summary, authenticationExpired || healthAuthenticationExpired)
        else GarminConnectHistorySyncResult.Completed(summary)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { tokenStore.clear() }

    private suspend fun refreshAndRetry(
        target: LocalDateTime,
        previous: GarminSession
    ): GarminConnectSyncResult {
        val refreshed = auth.refresh(previous) ?: run {
            tokenStore.clear()
            return GarminConnectSyncResult.AuthenticationExpired
        }
        tokenStore.save(refreshed)
        return try {
            val activity = activities.findActivityNear(refreshed, target)
                ?: return GarminConnectSyncResult.NoMatchingActivity
            val original = activities.downloadOriginal(refreshed, activity.activityId)
            try {
                when (val saved = fitImporter.import(activity.activityId, original)) {
                    is GarminFitImportResult.Imported -> GarminConnectSyncResult.Imported(saved.workout, saved.sha256)
                    is GarminFitImportResult.AlreadyImported -> GarminConnectSyncResult.AlreadyImported(saved.workout)
                    else -> GarminConnectSyncResult.Failed
                }
            } finally {
                original.fill(0)
            }
        } catch (error: GarminApiException) {
            if (error.statusCode == HTTP_FORBIDDEN || error.statusCode == HTTP_TOO_MANY_REQUESTS) {
                GarminConnectSyncResult.AccessBlocked
            } else GarminConnectSyncResult.Failed
        } catch (_: Exception) {
            GarminConnectSyncResult.Failed
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DOWNLOAD_PACING_MILLIS = 1_000L
    }
}
