package org.jetbrains.skiko

/** Leaves message-pump time between synchronous live-resize renders. */
internal class LiveResizeRenderThrottle(framesPerSecond: Int) {
    private val intervalMillis =
        ((1_000L + framesPerSecond.requirePositive() - 1L) / framesPerSecond).coerceAtLeast(1L)
    private var nextRenderMillis: Long? = null

    fun shouldRequestRender(nowMillis: Long): Boolean =
        nextRenderMillis?.let { nowMillis >= it } ?: true

    fun onRenderCompleted(nowMillis: Long) {
        nextRenderMillis = nowMillis + intervalMillis
    }

    fun reset() {
        nextRenderMillis = null
    }

    private fun Int.requirePositive(): Int =
        also { require(it > 0) { "framesPerSecond must be positive" } }
}
