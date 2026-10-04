package app.limits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAnalysisTest {
    @Test
    fun paceRatio_isOneWhenUsageMatchesElapsedWindowFraction() {
        val now = 1_000_000_000L
        val fiveHours = 5L * 60 * 60 * 1000
        val window = QuotaWindow(
            id = "five_hour",
            label = "5 hour",
            usedPercent = 50.0,
            resetsAtEpochMillis = now + fiveHours / 2,
        )

        assertEquals(1.0, window.paceRatio(now)!!, 0.001)
    }

    @Test
    fun paceRatio_isAboveOneWhenBurningTooFast() {
        val now = 1_000_000_000L
        val fiveHours = 5L * 60 * 60 * 1000
        val window = QuotaWindow(
            id = "rolling",
            label = "Rolling",
            usedPercent = 75.0,
            resetsAtEpochMillis = now + fiveHours / 2,
        )

        assertTrue(window.paceRatio(now)!! > 1.0)
    }

    @Test
    fun paceRatio_isUnknownForMonthlyWindow() {
        val window = QuotaWindow(
            id = "monthly",
            label = "Monthly",
            usedPercent = 10.0,
            resetsAtEpochMillis = System.currentTimeMillis() + 1_000,
        )

        assertNull(window.paceRatio())
    }

    @Test
    fun usageBecomesStaleAfterThirtyMinutes() {
        val now = 10_000_000L
        val fresh = ProviderUsage(
            provider = ProviderId.CLAUDE,
            windows = emptyList(),
            fetchedAtEpochMillis = now - 29L * 60 * 1000,
        )
        val stale = fresh.copy(fetchedAtEpochMillis = now - 31L * 60 * 1000)

        assertFalse(fresh.isStale(now))
        assertTrue(stale.isStale(now))
    }
}
