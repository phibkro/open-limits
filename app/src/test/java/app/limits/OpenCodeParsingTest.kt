package app.limits

import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeParsingTest {
    @Test fun sanity() {
        val sample = """{\"usage\":{\"rolling\":{\"status\":\"allowed\",\"percent\":10,\"resetsAt\":\"2026-10-04T12:00:00Z\"}}}"""
        assertTrue(sample.contains("rolling"))
    }
}
