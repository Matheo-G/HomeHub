package com.homehub.launcher.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Limitation de débit des sessions de l'API locale (50 messages/s). */
class RateLimiterTest {

    @Test
    fun acceptsUpToTheLimitWithinOneSecond() {
        val limiter = RateLimiter(perSecond = 3)
        val accepted = (1..5).count { limiter.tryAcquire(now = 10_000L) }
        assertEquals(3, accepted)
    }

    @Test
    fun resetsAfterOneSecond() {
        val limiter = RateLimiter(perSecond = 2)
        assertTrue(limiter.tryAcquire(now = 0L))
        assertTrue(limiter.tryAcquire(now = 100L))
        assertFalse(limiter.tryAcquire(now = 200L))
        assertTrue(limiter.tryAcquire(now = 1_100L))
    }
}
