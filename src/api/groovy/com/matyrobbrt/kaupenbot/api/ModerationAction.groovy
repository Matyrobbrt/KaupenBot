package com.matyrobbrt.kaupenbot.api

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.POJO
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable

import java.time.Duration
import java.util.concurrent.TimeUnit

@CompileStatic
@ApiStatus.NonExtendable
abstract class ModerationAction<CONTEXT> {
    @PackageScope
    ModerationAction() {}

    static <CONTEXT> ModerationAction<CONTEXT> ban() {
        return new Base<>(ModerationActionType.BAN, null, null)
    }
    static <CONTEXT> ModerationAction<CONTEXT> kick() {
        return new Base<>(ModerationActionType.KICK, null, null)
    }
    static <CONTEXT> ModerationAction<CONTEXT> timeout(Duration duration) {
        return new Base<>(ModerationActionType.TIMEOUT, duration, null)
    }

    @Nullable
    abstract Duration getDuration()
    abstract ModerationActionType getType()
    abstract String getReason(CONTEXT context, String orElse)

    abstract ModerationAction<CONTEXT> withDuration(Duration duration)
    abstract ModerationAction<CONTEXT> withReason(@ClosureParams(
            value = FromString,
            options = 'CONTEXT'
    ) Closure<String> reasonFunction)

    @CompileStatic
    private static final class Base<Z> extends ModerationAction<Z> {
        private final ModerationActionType type
        private final Duration duration
        @Nullable
        private final Closure<String> reason

        Base(ModerationActionType type, Duration duration, Closure<String> reason) {
            this.type = type
            this.duration = duration
            this.reason = reason
        }

        @Override
        Duration getDuration() {
            return duration
        }

        @Override
        ModerationActionType getType() {
            return type
        }

        @Override
        ModerationAction<Z> withDuration(Duration duration) {
            if (!type.supportsDuration()) {
                throw new IllegalArgumentException("The moderation action type $type does not support durations!")
            }
            return new Base<Z>(this.type, duration, this.reason)
        }

        @Override
        ModerationAction<Z> withReason(@ClosureParams(
                value = FromString,
                options = 'Z'
        ) Closure<String> reasonFunction) {
            return new Base<Z>(this.type, this.duration, reasonFunction)
        }

        @Override
        String getReason(Z z, String orElse) {
            return reason?.call(z) ?: orElse
        }
    }
}

@POJO
@CompileStatic
enum ModerationActionType {
    KICK('👢', ActionType.KICK),
    TIMEOUT('🔇', ActionType.MEMBER_UPDATE),
    BAN('🔨', ActionType.BAN)

    ModerationActionType(String emoji, ActionType auditType) {
        this.emoji = emoji
        this.auditType = auditType
    }

    final String emoji
    final ActionType auditType

    // TODO duration for bans as well?
    boolean supportsDuration() {
        this == TIMEOUT
    }

    String asButtonLabel(@Nullable Duration duration) {
        final text = switch (this) {
            case KICK -> 'Kick'
            case BAN -> 'Ban'
            case TIMEOUT -> "Timeout for ${formatDuration(duration)}"
        }
        return emoji + ' ' + text
    }

    ActionType auditType() {
        return this.auditType
    }

    static String formatDuration(Duration duration) {
        List<String> parts = new ArrayList<>()
        long days = duration.toDaysPart()
        if (days > 0) {
            parts.add(plural(days, 'day'))
        }
        int hours = duration.toHoursPart()
        if (hours > 0) {
            parts.add(plural(hours, 'hour'))
        }
        int minutes = duration.toMinutesPart()
        if (minutes > 0) {
            parts.add(plural(minutes, 'minute'))
        }
        int seconds = duration.toSecondsPart()
        if (seconds > 0) {
            parts.add(plural(seconds, 'second'))
        }

        if (parts.isEmpty()) {
            parts.add(plural(hours, 'hour'))
        }

        return parts.join(', ')
    }

    private static String plural(long num, String unit) {
        return num + ' ' + unit + (num == 1 ? '' : 's')
    }

    AuditableRestAction<Void> apply(Member member, @Nullable Duration duration) {
        return switch (this) {
            case KICK -> member.kick()
            case BAN -> member.ban(0, TimeUnit.DAYS)
            case TIMEOUT -> member.timeoutFor(duration)
        }
    }
}
