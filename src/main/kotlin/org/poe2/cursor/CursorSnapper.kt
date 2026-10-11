package org.poe2.cursor

import java.util.concurrent.TimeUnit
import org.poe2.geometry.EllipticalRange

internal const val DEFAULT_SNAP_INTERVAL_MS = 100L
private val DEFAULT_SNAP_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(DEFAULT_SNAP_INTERVAL_MS)
private const val UNSET_KEY = -1

/** Called on one worker thread; no queued cursor moves and no key-repeat input generation. */
class CursorSnapper(private val input: KeyboardAndCursor, private val intervalNanos: Long = DEFAULT_SNAP_INTERVAL_NANOS) {
    private enum class TriggerState { WAITING_FOR_RELEASE, RELEASED, HELD }
    private var triggerState = TriggerState.WAITING_FOR_RELEASE
    private var nextCheck = 0L
    private var previousKey = UNSET_KEY

    /** Cancels the held trigger; another snap requires releasing it first. */
    fun requireTriggerRelease() {
        triggerState = TriggerState.WAITING_FOR_RELEASE
        nextCheck = 0L
    }

    /** Checks the trigger and repeat delay; returns true only when the cursor moved. */
    fun snapWhileTriggerHeld(now: Long, key: Int, range: EllipticalRange, eligible: () -> Boolean): Boolean {
        if (key != previousKey) {
            requireTriggerRelease()
            previousKey = key
        }
        if (!eligible()) {
            requireTriggerRelease()
            return false
        }
        val down = input.isKeyDown(key)
        if (!down) {
            triggerState = TriggerState.RELEASED
            nextCheck = 0L
            return false
        }
        if (triggerState == TriggerState.WAITING_FOR_RELEASE) return false
        if (triggerState == TriggerState.HELD && now < nextCheck) return false
        triggerState = TriggerState.HELD
        nextCheck = now + intervalNanos
        val destination = range.findSnapPoint(input.readCursorPosition()) ?: return false
        if (!eligible() || !input.isKeyDown(key)) return false
        input.moveCursorTo(destination)
        return true
    }
}
