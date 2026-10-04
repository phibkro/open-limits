package app.limits

import android.app.Application
import app.limits.sync.UsageRefreshWorker

class LimitsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        UsageRefreshWorker.schedule(this)
    }
}
