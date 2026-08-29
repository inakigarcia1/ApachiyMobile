package com.nuvio.app.core.auth

import co.touchlab.kermit.Logger
import com.nuvio.app.core.device.ApachiyDeviceApi
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

object RemoteLogoutWatcher {
    private val log = Logger.withTag("RemoteLogoutWatcher")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null
    private var pollJob: Job? = null
    private var observingStarted = false

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
        watchJob = scope.launch { watchRealtimeDeletes() }
        pollJob = scope.launch { pollDeviceList() }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        pollJob?.cancel()
        watchJob = null
        pollJob = null
    }

    private suspend fun watchRealtimeDeletes() {
        val installationId = SyncClientIdentity.currentClientId()
        val channel = SupabaseProvider.client.channel("apachiy-device-logout-$installationId")
        try {
            val changes = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "user_devices"
            }
            coroutineScope {
                val job = launch {
                    changes.collect { action ->
                        val deletedInstallationId = action.oldRecord["installation_id"]
                            ?.jsonPrimitive?.contentOrNull
                        val deletedDeviceId = action.oldRecord["id"]
                            ?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val registeredDeviceId = SyncClientIdentity.loadRegisteredDeviceId()
                        val matchesInstallation =
                            deletedInstallationId != null && deletedInstallationId == installationId
                        val matchesDeviceId =
                            deletedDeviceId != null &&
                                registeredDeviceId != null &&
                                deletedDeviceId == registeredDeviceId
                        val missingLocally = !matchesInstallation && !matchesDeviceId &&
                            stillRegisteredOnApi(installationId).not()
                        if (!matchesInstallation && !matchesDeviceId && !missingLocally) return@collect
                        log.w { "device row deleted remotely; signing out" }
                        AuthRepository.signOut()
                    }
                }
                channel.subscribe(blockUntilSubscribed = true)
                job.join()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "remote logout watcher failed" }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }

    private suspend fun pollDeviceList() {
        delay(LIST_POLL_INITIAL_DELAY_MS)
        while (true) {
            val installationId = SyncClientIdentity.currentClientId()
            if (!stillRegisteredOnApi(installationId)) {
                log.w { "device missing from API list; signing out" }
                AuthRepository.signOut()
                return
            }
            delay(LIST_POLL_INTERVAL_MS)
        }
    }

    private suspend fun stillRegisteredOnApi(installationId: String): Boolean {
        if (ApachiyConfig.API_BASE_URL.isBlank()) return true
        if (SyncClientIdentity.loadRegisteredDeviceId() == null) return true
        val token = runCatching {
            SupabaseProvider.client.auth.currentAccessTokenOrNull()
        }.getOrNull() ?: return true
        return runCatching {
            val response = ApachiyDeviceApi.getDevices(token)
            if (response.status !in 200..299) return@runCatching true
            val rows = ApachiyDeviceApi.decodeDeviceList(response.body)
            rows.any { it.resolvedInstallationId.equals(installationId, ignoreCase = true) }
        }.getOrDefault(true)
    }

    private const val LIST_POLL_INITIAL_DELAY_MS = 15_000L
    private const val LIST_POLL_INTERVAL_MS = 45_000L
}
