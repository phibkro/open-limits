package app.limits.sync

import app.limits.data.CredentialStore
import app.limits.data.OpenCodeCredential
import app.limits.data.UsageStore
import app.limits.domain.ProviderId
import app.limits.domain.ProviderUsage
import app.limits.network.Http
import app.limits.providers.ClaudeProvider
import app.limits.providers.CodexProvider
import app.limits.providers.OpenCodeProvider
import app.limits.providers.QuotaProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsageRepository(
    private val credentials: CredentialStore,
    private val store: UsageStore,
    http: Http,
    private val onDataChanged: suspend () -> Unit = {},
) {
    val claude = ClaudeProvider(credentials, http)
    val codex = CodexProvider(credentials, http)
    val openCode = OpenCodeProvider(credentials, http)
    private val providers: Map<ProviderId, QuotaProvider> =
        listOf(claude, codex, openCode).associateBy { it.id }

    private val _snapshots = MutableStateFlow(store.getAll())
    val snapshots: StateFlow<Map<ProviderId, ProviderUsage>> = _snapshots.asStateFlow()

    private val _errors = MutableStateFlow(store.getErrors())
    val errors: StateFlow<Map<ProviderId, String>> = _errors.asStateFlow()

    private val _connections = MutableStateFlow(connectedSet())
    val connections: StateFlow<Set<ProviderId>> = _connections.asStateFlow()

    suspend fun refreshAll() = coroutineScope {
        val pending = providers.values
            .filter { it.isConnected() }
            .map { provider -> async { provider.id to runCatching { provider.fetch() } } }

        var changed = false
        for (deferred in pending) {
            val (id, result) = deferred.await()
            result.fold(
                onSuccess = { usage ->
                    store.save(usage)
                    store.clearError(id)
                    _snapshots.value = store.getAll()
                    _errors.value = store.getErrors()
                    changed = true
                },
                onFailure = { error ->
                    store.saveError(id, error.message ?: "Refresh failed")
                    _errors.value = store.getErrors()
                },
            )
        }
        refreshConnections()
        if (changed) onDataChanged()
    }

    suspend fun refresh(providerId: ProviderId) {
        val provider = providers.getValue(providerId)
        if (!provider.isConnected()) return

        val result = runCatching { provider.fetch() }
        result.fold(
            onSuccess = {
                store.save(it)
                store.clearError(providerId)
                _snapshots.value = store.getAll()
                _errors.value = store.getErrors()
            },
            onFailure = {
                store.saveError(providerId, it.message ?: "Refresh failed")
                _errors.value = store.getErrors()
            },
        )
        refreshConnections()
        if (result.isSuccess) onDataChanged()
    }

    fun saveOpenCodeKey(key: String) {
        credentials.saveOpenCode(OpenCodeCredential(key.trim()))
        refreshConnections()
    }

    suspend fun disconnect(providerId: ProviderId) {
        providers.getValue(providerId).disconnect()
        store.clear(providerId)
        _snapshots.value = store.getAll()
        _errors.value = store.getErrors()
        refreshConnections()
        onDataChanged()
    }

    fun refreshConnections() {
        _connections.value = connectedSet()
    }

    private fun connectedSet() =
        providers.values.filter { it.isConnected() }.map { it.id }.toSet()
}
