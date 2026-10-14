package com.distribuidora.vendedor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTest {

    @Test
    fun `toUtc normalizes a La Paz offset to Z`() {
        assertEquals("2026-10-14T13:15:00Z", toUtc("2026-10-14T09:15:00-04:00"))
    }

    @Test
    fun `toUtc normalizes the PostgREST plus-zero offset`() {
        assertEquals("2026-10-14T13:15:00.123456Z", toUtc("2026-10-14T13:15:00.123456+00:00"))
        assertEquals("2026-10-14T13:15:00Z", toUtc("2026-10-14T13:15:00Z"))
    }

    @Test
    fun `formatLaPaz converts UTC to local day and time`() {
        assertEquals("14/10 09:15", formatLaPaz("2026-10-14T13:15:00Z"))
        assertEquals("15/10 00:30", formatLaPaz("2026-10-15T04:30:00+00:00"))
    }

    @Test
    fun `nowUtc is ISO-8601 in UTC`() {
        val now = nowUtc()
        assertTrue(now, now.endsWith("Z"))
        assertEquals(now, toUtc(now))
    }
}
