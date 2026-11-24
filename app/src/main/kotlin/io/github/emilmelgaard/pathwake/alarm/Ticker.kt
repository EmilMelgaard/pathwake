package io.github.emilmelgaard.pathwake.alarm

import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time now and at the start of every following minute (for "rings in ..." texts). */
fun minuteTicker(now: () -> ZonedDateTime): Flow<ZonedDateTime> = flow {
    while (true) {
        val time = now()
        emit(time)
        val nextMinute = time.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        delay(Duration.between(time, nextMinute).toMillis() + 50)
    }
}
