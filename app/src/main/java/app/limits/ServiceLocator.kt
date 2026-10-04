package app.limits

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.limits.data.CredentialStore
import app.limits.data.UsageHistoryStore
import app.limits.data.UsageStore
import app.limits.network.Http
import app.limits.sync.UsageRepository
import app.limits.widget.ComparisonLimitsWidget
import app.limits.widget.LimitsWidget
import app.limits.widget.RadialLimitsWidget

object ServiceLocator {
    @Volatile private var repositoryInstance: UsageRepository? = null

    fun init(context: Context) {
        if (repositoryInstance == null) synchronized(this) {
            if (repositoryInstance == null) {
                val app = context.applicationContext
                repositoryInstance = UsageRepository(
                    credentials = CredentialStore(app),
                    store = UsageStore(app),
                    historyStore = UsageHistoryStore(app),
                    http = Http(),
                    onDataChanged = {
                        LimitsWidget().updateAll(app)
                        RadialLimitsWidget().updateAll(app)
                        ComparisonLimitsWidget().updateAll(app)
                    },
                )
            }
        }
    }

    val repository: UsageRepository
        get() = checkNotNull(repositoryInstance) { "ServiceLocator.init must be called first" }
}
