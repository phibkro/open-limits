package app.limits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedUsageTest {
    @Test
    fun remainingPercent_invertsProviderUsage() {
        val window = QuotaWindow(
            id = "five_hour",
            label = "5 hour",
            usedPercent = 63.0,
        )

        assertEquals(37.0, window.remainingPercent, 0.001)
    }

    @Test
    fun claude_mapsFiveHourAndWeeklyButNoSyntheticTotal() {
        val usage = ProviderUsage(
            provider = ProviderId.CLAUDE,
            windows = listOf(
                QuotaWindow("five_hour", "5 hour", 25.0),
                QuotaWindow("seven_day", "Weekly", 40.0),
            ),
        )

        val normalized = usage.normalizedMeasurements()

        assertEquals(75.0, normalized.getValue(MeasurementKind.SHORT).remainingPercent, 0.001)
        assertEquals(60.0, normalized.getValue(MeasurementKind.WEEKLY).remainingPercent, 0.001)
        assertFalse(normalized.containsKey(MeasurementKind.TOTAL))
    }

    @Test
    fun openCodeMapsRollingWeeklyAndMonthly() {
        val usage = ProviderUsage(
            provider = ProviderId.OPENCODE_GO,
            windows = listOf(
                QuotaWindow("rolling", "Rolling", 10.0),
                QuotaWindow("weekly", "Weekly", 20.0),
                QuotaWindow("monthly", "Monthly", 30.0),
            ),
        )

        val normalized = usage.normalizedMeasurements()

        assertEquals(90.0, normalized.getValue(MeasurementKind.SHORT).remainingPercent, 0.001)
        assertEquals(80.0, normalized.getValue(MeasurementKind.WEEKLY).remainingPercent, 0.001)
        assertEquals(70.0, normalized.getValue(MeasurementKind.TOTAL).remainingPercent, 0.001)
    }

    @Test
    fun codexKeepsUnknownPrimarySecondaryAsComparableBuckets() {
        val usage = ProviderUsage(
            provider = ProviderId.CODEX,
            windows = listOf(
                QuotaWindow("primary", "Primary", 15.0),
                QuotaWindow("secondary", "Secondary", 35.0),
            ),
        )

        val normalized = usage.normalizedMeasurements()

        assertEquals("primary", normalized.getValue(MeasurementKind.SHORT).sourceWindowId)
        assertEquals("secondary", normalized.getValue(MeasurementKind.WEEKLY).sourceWindowId)
        assertNull(normalized[MeasurementKind.TOTAL])
    }
}
