package com.example.toothtimer

import org.junit.Assert.*
import org.junit.Test

class SessionClockTest {
    private fun nextTransition(): SessionClock = SessionClock(listOf(1000, 1000), 2000)
        .advance(0, true).advance(1000, true)

    @Test fun longAudioExtendsTransition() {
        val waiting = nextTransition().advance(2000, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, waiting.phase)
        assertEquals(1, waiting.completedSteps)
        val stillWaiting = waiting.advance(1000, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, stillWaiting.phase)
        val started = stillWaiting.advance(0, true)
        assertEquals(CleaningPhase.STEP, started.phase)
        assertEquals(1000L, started.remainingMs)
    }
    @Test fun shortAudioDoesNotShortenTransition() {
        val waiting = nextTransition().advance(1000, true)
        assertEquals(CleaningPhase.BETWEEN_STEPS, waiting.phase)
        assertEquals(CleaningPhase.STEP, waiting.advance(1000, true).phase)
    }
    @Test fun noPauseStillWaitsForPlaylist() {
        val initial = SessionClock(listOf(1000), 0)
        assertEquals(CleaningPhase.BETWEEN_STEPS, initial.advance(5000, false).phase)
        assertEquals(CleaningPhase.STEP, initial.advance(5000, true).phase)
    }
    @Test fun progressUsesFractionalSecondsAndExcludesTransitions() {
        val partial = SessionClock(listOf(1000, 1000), 2000).advance(0, true).advance(250, true)
        assertEquals(.25f, partial.zoneProgress)
        assertEquals(.125f, partial.cleaningProgress)
        assertEquals(.5f, nextTransition().advance(9000, false).cleaningProgress)
    }
    @Test fun completesExactlyOnceAndCountsLastZone() {
        val finished = nextTransition().advance(2000, true).advance(1000, true)
        assertEquals(CleaningPhase.COMPLETED, finished.phase)
        assertEquals(2, finished.completedSteps)
        assertEquals(1f, finished.cleaningProgress)
        assertEquals(finished, finished.advance(1000, true))
    }
}
