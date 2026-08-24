package org.jetbrains.skiko

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveResizeRenderThrottleTest {
    @Test
    fun leavesTimeForWindowMessagesAfterRenderCompletion() {
        val throttle = LiveResizeRenderThrottle(framesPerSecond = 60)

        assertTrue(throttle.shouldRequestRender(100L))
        throttle.onRenderCompleted(125L)

        assertFalse(throttle.shouldRequestRender(125L))
        assertFalse(throttle.shouldRequestRender(141L))
        assertTrue(throttle.shouldRequestRender(142L))
    }

    @Test
    fun resetAllowsFinalResizeRenderImmediately() {
        val throttle = LiveResizeRenderThrottle(framesPerSecond = 60)

        throttle.onRenderCompleted(100L)
        assertFalse(throttle.shouldRequestRender(101L))

        throttle.reset()

        assertTrue(throttle.shouldRequestRender(101L))
    }
}
