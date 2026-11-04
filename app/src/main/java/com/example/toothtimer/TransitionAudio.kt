package com.example.toothtimer

import kotlin.random.Random

/** Choose one pause sound; zone announcements belong to STEP, not BETWEEN_STEPS. */
fun selectPauseCue(
    pauseCues: List<AudioCue>,
    stepIndex: Int,
    random: Random = Random.Default
): AudioCue? = if (stepIndex > 0) pauseCues.randomOrNull(random) else null

fun phaseAudioUris(phase: CleaningPhase, pauseCueUri: String?, zoneCues: List<AudioCue>): List<String> =
    when (phase) {
        CleaningPhase.BETWEEN_STEPS -> listOfNotNull(pauseCueUri)
        CleaningPhase.STEP -> zoneCues.map { it.uri }
        CleaningPhase.COMPLETED -> emptyList()
    }
