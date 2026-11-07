package com.example.toothtimer

import kotlin.random.Random

fun selectRandomCue(cues: List<AudioCue>, random: Random = Random.Default): AudioCue? = cues.randomOrNull(random)

/** Choose once per pause or zone, so pause/resume and recomposition never re-roll. */
fun selectPauseCue(
    pauseCues: List<AudioCue>,
    stepIndex: Int,
    random: Random = Random.Default
): AudioCue? = if (stepIndex > 0) selectRandomCue(pauseCues, random) else null

fun selectCompletionCue(
    cues: List<AudioCue>,
    status: SessionStatus,
    random: Random = Random.Default
): AudioCue? = if (status == SessionStatus.COMPLETED) selectRandomCue(cues, random) else null

fun phaseAudioUris(phase: CleaningPhase, pauseCueUri: String?, zoneCueUri: String?): List<String> =
    when (phase) {
        CleaningPhase.BETWEEN_STEPS -> listOfNotNull(pauseCueUri)
        CleaningPhase.STEP -> listOfNotNull(zoneCueUri)
        // The result screen owns completion audio after the cleaning screen is disposed.
        CleaningPhase.COMPLETED -> emptyList()
    }
