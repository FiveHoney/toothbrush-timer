package com.example.toothtimer

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TransitionAudioTest {
    private val pool = listOf(AudioCue("pause-a", "A"), AudioCue("pause-b", "B"), AudioCue("pause-c", "C"))
    private val zone = listOf(AudioCue("zone-a", "Zone A"), AudioCue("zone-b", "Zone B"))

    @Test fun firstZoneHasOnlyItsOwnAnnouncements() {
        assertEquals(zone, transitionAudio(pool, zone, 0, Random(1)))
    }

    @Test fun emptyPoolPreservesZonePlaylist() {
        assertEquals(zone, transitionAudio(emptyList(), zone, 1))
        assertTrue(transitionAudio(emptyList(), emptyList(), 1).isEmpty())
    }

    @Test fun eachPauseGetsExactlyOneRandomCueThenZoneAnnouncements() {
        val random = Random(42)
        val selected = (1..100).map { index ->
            val queue = transitionAudio(pool, zone, index, random)
            assertEquals(3, queue.size)
            assertTrue(queue.first() in pool)
            assertEquals(zone, queue.drop(1))
            queue.first()
        }
        // Seeded selection is deterministic; all variants must be reachable.
        assertEquals(pool.toSet(), selected.toSet())
    }

    @Test fun singleCueWorksForEveryPauseWithoutZoneAudio() {
        repeat(5) { assertEquals(pool.take(1), transitionAudio(pool.take(1), emptyList(), it + 1)) }
    }

    @Test fun twoSecondPauseWaitsForThreeSecondCueAndFullNextZone() {
        val queue = transitionAudio(pool, emptyList(), 1, Random(1))
        assertEquals(1, queue.size)
        val transition = SessionClock(listOf(1000, 5000), 2000).advance(0, true).advance(1000, true)
        val expired = transition.advance(2000, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, expired.phase)
        val stillPlaying = expired.advance(999, false)
        assertEquals(CleaningPhase.BETWEEN_STEPS, stillPlaying.phase)
        val nextZone = stillPlaying.advance(1, true)
        assertEquals(CleaningPhase.STEP, nextZone.phase)
        assertEquals(5000L, nextZone.remainingMs)
    }
}
