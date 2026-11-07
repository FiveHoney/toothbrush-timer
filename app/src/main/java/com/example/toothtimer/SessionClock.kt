package com.example.toothtimer

/** Immutable clock: audio and the minimum transition time must both finish. */
data class SessionClock(
    val durationsMs: List<Long>,
    val pauseMs: Long,
    val index: Int = 0,
    val phase: CleaningPhase = CleaningPhase.BETWEEN_STEPS,
    val phaseElapsedMs: Long = 0,
    val elapsedMs: Long = 0
) {
    val transitionMs: Long get() = if (index == 0) 0 else pauseMs
    val remainingMs: Long get() = (durationsMs[index] - phaseElapsedMs).coerceAtLeast(0)
    val completedSteps: Int get() = index + if (phase == CleaningPhase.COMPLETED) 1 else 0
    val zoneProgress: Float get() = (phaseElapsedMs.toFloat() / durationsMs[index]).coerceIn(0f, 1f)
    val cleaningProgress: Float get() = ((durationsMs.take(index).sum() +
        if (phase == CleaningPhase.STEP) phaseElapsedMs.coerceAtMost(durationsMs[index])
        else if (phase == CleaningPhase.COMPLETED) durationsMs[index] else 0L).toFloat() / durationsMs.sum()).coerceIn(0f, 1f)

    fun advance(deltaMs: Long, audioFinished: Boolean): SessionClock {
        if (phase == CleaningPhase.COMPLETED) return this
        val delta = deltaMs.coerceAtLeast(0)
        val time = phaseElapsedMs + delta
        val next = copy(phaseElapsedMs = time, elapsedMs = elapsedMs + delta)
        return when (phase) {
            CleaningPhase.BETWEEN_STEPS -> if (time >= transitionMs && audioFinished)
                next.copy(phase = CleaningPhase.STEP, phaseElapsedMs = 0) else next
            CleaningPhase.STEP -> if (time >= durationsMs[index]) {
                if (index == durationsMs.lastIndex) next.copy(phase = CleaningPhase.COMPLETED)
                else next.copy(index = index + 1, phase = CleaningPhase.BETWEEN_STEPS, phaseElapsedMs = 0)
            } else next
            CleaningPhase.COMPLETED -> this
        }
    }
}
