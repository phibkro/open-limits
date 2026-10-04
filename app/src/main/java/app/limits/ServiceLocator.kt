package app.limits

import android.content.Context
import app.limits.data.CredentialStore
import app.limits.data.UsageStore
import app.limits.network.Http
import app.limits.sync.UsageRepository

object ServiceLocator {
    @Volatile private var repositoryInstance: UsageRepository? = null

    fun init(context: Context) {
        if (repositoryInstance == null) synchronized(this) {
            if (repositoryInstance == null) {
                val app = context.applicationContext
                repositoryInstance = UsageRepository(CredentialStore(app), UsageStore(app), Http())
            }
        }
    }

    val repository: UsageRepository
        get() = checkNotNull(repositoryInstance) { "ServiceLocator.init must be called first" }
}
