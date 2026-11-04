package com.example.toothtimer

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TransitionAudioTest {
    private val pool = listOf(AudioCue("pause-a", "A"), AudioCue("pause-b", "B"), AudioCue("pause-c", "C"))
    private val zone = listOf(AudioCue("zone-a", "Zone A"), AudioCue("zone-b", "Zone B"))

    @Test fun firstZoneStartsWithoutWaitingForItsAnnouncement() {
        assertNull(selectPauseCue(pool, 0, Random(1)))
        assertTrue(phaseAudioUris(CleaningPhase.BETWEEN_STEPS, null, zone).isEmpty())
        val started = SessionClock(listOf(5000), 2000).advance(0, true)
        assertEquals(CleaningPhase.STEP, started.phase)
        assertEquals(5000L, started.remainingMs)
        assertEquals(zone.map { it.uri }, phaseAudioUris(started.phase, null, zone))
    }

    @Test fun emptyPoolDoesNotMoveZoneAudioIntoThePause() {
        assertNull(selectPauseCue(emptyList(), 1))
        assertTrue(phaseAudioUris(CleaningPhase.BETWEEN_STEPS, null, zone).isEmpty())
        assertEquals(zone.map { it.uri }, phaseAudioUris(CleaningPhase.STEP, null, zone))
    }

    @Test fun eachPauseGetsOneRandomCueAndNoZoneAnnouncements() {
        val random = Random(42)
        val selected = (1..100).map { index ->
            val cue = selectPauseCue(pool, index, random)!!
            assertTrue(cue in pool)
            assertEquals(listOf(cue.uri), phaseAudioUris(CleaningPhase.BETWEEN_STEPS, cue.uri, zone))
            cue
        }
        assertEquals(pool.toSet(), selected.toSet())
    }

    @Test fun singleCueWorksForEveryPause() {
        repeat(5) { assertEquals(pool.first(), selectPauseCue(pool.take(1), it + 1)) }
    }

    @Test fun twoSecondPauseWaitsForThreeSecondCueThenStartsZoneAudio() {
        val cue = selectPauseCue(pool, 1, Random(1))!!
        val transition = SessionClock(listOf(1000, 5000), 2000).advance(0, true).advance(1000, true)
        val expired = transition.advance(2000, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, expired.phase)
        assertEquals(listOf(cue.uri), phaseAudioUris(expired.phase, cue.uri, zone))
        val stillPlaying = expired.advance(999, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, stillPlaying.phase)
        val nextZone = stillPlaying.advance(1, true)
        assertEquals(CleaningPhase.STEP, nextZone.phase)
        assertEquals(5000L, nextZone.remainingMs)
        assertEquals(zone.map { it.uri }, phaseAudioUris(nextZone.phase, cue.uri, zone))
    }

    @Test fun zoneCountdownAdvancesAndFinishesWhileZoneAudioIsStillPlaying() {
        val started = SessionClock(listOf(5000, 5000), 2000).advance(0, true)
        val counting = started.advance(1000, false)
        assertEquals(CleaningPhase.STEP, counting.phase)
        assertEquals(4000L, counting.remainingMs)
        val transition = counting.advance(4000, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, transition.phase)
        assertEquals(1, transition.index)
    }

    @Test fun completionHasNoAudio() {
        assertTrue(phaseAudioUris(CleaningPhase.COMPLETED, pool.first().uri, zone).isEmpty())
    }
}
