package com.matyrobbrt.kaupenbot.api.util

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

import java.time.Instant

@CompileStatic
@TupleConstructor
final class Warning {
    long userId, guildId, moderatorId
    UUID warnId
    String reason
    Instant timestamp
}