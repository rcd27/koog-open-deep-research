package com.github.rcd27.koogopendeepsearch.agent

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

val testClock: Clock = object : Clock {
    override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
}
