package com.nuvio.app.core.auth

import co.touchlab.kermit.Logger
import com.nuvio.app.core.device.ApachiyDeviceApi
import com.nuvio.app.core.device.DeviceRegistrar
import com.nuvio.app.core.network.ApachiyConfig
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.sync.SyncClientIdentity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.TimeMark
import kotlin.time.TimeSource

object RemoteLogoutWatcher {
    private val log = Logger.withTag("RemoteLogoutWatcher")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null
    private var pollJob: Job? = null
    private var observingStarted = false
    private var authenticatedAtMark: TimeMark? = null

    fun startObserving() {
        if (observingStarted) return
        observingStarted = true
        scope.launch {
            AuthRepository.state
                .map { state -> state is AuthState.Authenticated && !state.isAnonymous }
                .distinctUntilChanged()
                .collect { isAuthed ->
                    if (isAuthed) {
                        startWatching()
                    } else {
                        stopWatching()
                    }
                }
        }
    }

    private fun startWatching() {
        watchJob?.cancel()
        pollJob?.cancel()
        authenticatedAtMark = TimeSource.Monotonic.markNow()
        watchJob = scope.launch { watchRealtimeDeletes() }
        pollJob = scope.launch { pollDeviceList() }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        pollJob?.cancel()
        watchJob = null
        pollJob = null
        authenticatedAtMark = null
    }

    private suspend fun watchRealtimeDeletes() {
        while (true) {
            val installationId = SyncClientIdentity.currentClientId()
            val channel = SupabaseProvider.client.channel("apachiy-device-logout-$installationId")
            try {
                val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "user_devices"
                }
                coroutineScope {
                    val job = launch {
                        changes.collect { action ->
                            if (actionMatchesThisDevice(action, installationId) && shouldRemoteSignOut(installationId)) {
                                log.w { "device row changed remotely; signing out" }
                                AuthRepository.signOut()
                            }
                        }
                    }
                    channel.subscribe(blockUntilSubscribed = true)
                    job.join()
                }
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "remote logout watcher failed; retrying" }
                delay(8_000)
            } finally {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    private fun actionMatchesThisDevice(action: PostgresAction, installationId: String): Boolean {
        val record = when (action) {
            is PostgresAction.Delete -> action.oldRecord
            is PostgresAction.Update -> action.record
            else -> return false
        }
        val changedInstallationId = record["installation_id"]?.jsonPrimitive?.contentOrNull
        val changedDeviceId = record["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val registeredDeviceId = SyncClientIdentity.loadRegisteredDeviceId()
        val matchesInstallation =
            changedInstallationId != null && changedInstallationId == installationId
        val matchesDeviceId =
            changedDeviceId != null &&
                registeredDeviceId != null &&
                changedDeviceId == registeredDeviceId
        if (!matchesInstallation && !matchesDeviceId) return false
        if (action is PostgresAction.Update) {
            val revokedAt = record["revoked_at"]?.jsonPrimitive?.contentOrNull
            return !revokedAt.isNullOrBlank() && revokedAt != "null"
        }
        return true
    }

    private suspend fun pollDeviceList() {
        delay(LIST_POLL_INITIAL_DELAY_MS)
        while (true) {
            val installationId = SyncClientIdentity.currentClientId()
            if (shouldRemoteSignOut(installationId)) {
                log.w { "device missing from API list; signing out" }
                AuthRepository.signOut()
                return
            }
            delay(LIST_POLL_INTERVAL_MS)
        }
    }

    private suspend fun shouldRemoteSignOut(installationId: String): Boolean {
        if (isWithinRegistrationGracePeriod()) {
            log.d { "skipping remote sign-out during post-login registration grace period" }
            return false
        }
        if (SyncClientIdentity.loadRegisteredDeviceId() == null) {
            log.d { "skipping remote sign-out until device registration completes" }
            DeviceRegistrar.requestForegroundRegistration()
            return false
        }
        delay(DEVICE_CHANGE_CONFIRMATION_DELAY_MS)
        if (stillRegisteredOnApi(installationId)) {
            log.d { "device table change ignored; device still registered on API" }
            return false
        }
        return true
    }

    private fun isWithinRegistrationGracePeriod(): Boolean {
        val mark = authenticatedAtMark ?: return false
        return mark.elapsedNow().inWholeMilliseconds < REGISTRATION_GRACE_PERIOD_MS
    }

    private suspend fun stillRegisteredOnApi(installationId: String): Boolean {
        if (ApachiyConfig.API_BASE_URL.isBlank()) return true
        val token = runCatching {
            SupabaseProvider.client.auth.currentAccessTokenOrNull()
        }.getOrNull() ?: return true
        return runCatching {
            val response = ApachiyDeviceApi.getDevices(token)
            when (response.status) {
                401, 403 -> return@runCatching true
                410, 423 -> return@runCatching false
                !in 200..299 -> return@runCatching true
            }
            val rows = ApachiyDeviceApi.decodeDeviceList(response.body) ?: return@runCatching true
            rows.any { it.resolvedInstallationId.equals(installationId, ignoreCase = true) }
        }.getOrDefault(true)
    }

    private const val LIST_POLL_INITIAL_DELAY_MS = 4_000L
    private const val LIST_POLL_INTERVAL_MS = 12_000L
    private const val REGISTRATION_GRACE_PERIOD_MS = 15_000L
    private const val DEVICE_CHANGE_CONFIRMATION_DELAY_MS = 2_000L
}
