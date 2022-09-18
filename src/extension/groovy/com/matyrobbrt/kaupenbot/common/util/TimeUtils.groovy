package com.matyrobbrt.kaupenbot.common.util

import groovy.transform.CompileStatic

import java.time.Duration
import java.time.temporal.ChronoUnit

@CompileStatic
final class TimeUtils {

    /**
     * Decodes time from a string.
     *
     * @param time the time to decode
     * @return the decoded time.
     */
    static Time decodeTime(final String time) {
        final var unit = switch (time.charAt(time.length() - 1)) {
            case 'n' -> ChronoUnit.NANOS
            case 'l' -> ChronoUnit.MILLIS
            case 's' -> ChronoUnit.SECONDS
            case 'h' -> ChronoUnit.HOURS
            case 'd' -> ChronoUnit.DAYS
            case 'w' -> ChronoUnit.WEEKS
            case 'M' -> ChronoUnit.MONTHS
            case 'y' -> ChronoUnit.YEARS
            default -> ChronoUnit.MINUTES
        }
        final var tm = Long.parseLong(time.substring(0, time.length() - 1))
        return new Time(tm, unit)
    }

    private static List<String> splitInput(String str) {
        final var list = new ArrayList<String>()
        var builder = new StringBuilder()
        for (final ch : str.toCharArray()) {
            builder.append(ch)
            if (!Character.isDigit(ch)) {
                list.add(builder.toString())
                builder = new StringBuilder()
            }
        }
        return list
    }

    static Duration getDurationFromInput(String input) {
        final var data = splitInput(input)
        var duration = Duration.ofSeconds(0)
        for (final dt : data) {
            final var time = decodeTime(dt)
            final var asSeconds = time.amount() * time.unit().getDuration().getSeconds()
            duration = duration.plus(asSeconds, ChronoUnit.SECONDS)
        }
        return duration
    }

    static record Time(long amount, ChronoUnit unit) {}
}
