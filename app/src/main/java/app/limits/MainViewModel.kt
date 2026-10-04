package app.limits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.limits.domain.ProviderId
import app.limits.sync.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository: UsageRepository = ServiceLocator.repository
    val snapshots = repository.snapshots
    val connections = repository.connections
    val errors = repository.errors

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repository.refreshAll() }
        _refreshing.value = false
    }

    fun disconnect(provider: ProviderId) = viewModelScope.launch {
        repository.disconnect(provider)
    }
}
