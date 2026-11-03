package com.example.toothtimer

import kotlin.random.Random

/** One independent random pause cue, followed by the existing zone announcements. */
fun transitionAudio(
    pauseCues: List<AudioCue>,
    zoneCues: List<AudioCue>,
    stepIndex: Int,
    random: Random = Random.Default
): List<AudioCue> {
    val pauseCue = if (stepIndex > 0) pauseCues.randomOrNull(random) else null
    return listOfNotNull(pauseCue) + zoneCues
}
