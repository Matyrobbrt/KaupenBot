/*
 * ReLauncher - https://github.com/MinecraftModDevelopment/ReLauncher
 * Copyright (C) 2016-2022 <MMD - MinecraftModDevelopment>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * Specifically version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 */
package com.matyrobbrt.kaupenbot.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.helpers.MessageFormatter;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiscordLogbackLayout extends LayoutBase<ILoggingEvent> {
    /**
     * The emote used when the given {@link Level} does not have a corresponding emote in {@link #LEVEL_TO_EMOTE}.
     */
    private static final String UNKNOWN_EMOTE = ":radio_button:";
    /**
     * An {@linkplain Map immutable map} of {@link Level}s to emotes.
     * <p>
     * Used for visual distinction of log messages within the Discord console channel.
     */
    public static final Map<Level, String> LEVEL_TO_EMOTE = Map.of(
        Level.ERROR, ":red_square:",
        Level.WARN, ":yellow_circle:",
        Level.INFO, ":white_medium_small_square:",
        Level.DEBUG, ":large_blue_diamond:",
        Level.TRACE, ":small_orange_diamond:"
    );
    /**
     * The maximum length in characters of a stacktrace printed by this layout. If the stacktrace exceeds this length,
     * the stacktrace is truncated to this length, and a small snippet is appended with information about the truncated
     * portion of the stacktrace.
     */
    private static final int MAXIMUM_STACKTRACE_LENGTH = 1700;

    private static Object tryFormat(final Object obj) {
        if (obj instanceof IMentionable) {
            String name = null;
            if (obj instanceof User) {
                name = ((User) obj).getAsTag();
            } else if (obj instanceof Role) {
                name = ((Role) obj).getName();
            } else if (obj instanceof GuildChannel) {
                name = ((GuildChannel) obj).getName();
            } else if (obj instanceof Emoji emoji) {
                name = emoji.getName();
            }
            if (name != null) {
                return String.format("%s (%s;`%s`)", ((IMentionable) obj).getAsMention(), name, ((IMentionable)
                        obj).getIdLong());
            } else {
                return String.format("%s (`%s`)", ((IMentionable) obj).getAsMention(),
                        ((IMentionable) obj).getIdLong());
            }
        } else if (obj instanceof Collection<?> col) {
            final Stream<Object> stream = col.stream()
                .map(DiscordLogbackLayout::tryFormat);
            if (obj instanceof Set) {
                return stream.collect(Collectors.toSet());
            }
            return stream.collect(Collectors.toList());

        } else if (obj instanceof Map) {
            return ((Map<?, ?>) obj).entrySet().stream()
                .map(entry -> new AbstractMap.SimpleImmutableEntry<>(
                    tryFormat(entry.getKey()), tryFormat(entry.getValue())
                ))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        } else if (obj instanceof final Map.Entry<?, ?> entry) {
            return new AbstractMap.SimpleImmutableEntry<>(tryFormat(entry.getKey()),
                tryFormat(entry.getValue()));

        }
        return obj;
    }

    /**
     * {@inheritDoc}
     *
     * @param event the event
     * @return the string
     */
    @Override
    public String doLayout(final ILoggingEvent event) {
        final StringBuilder builder = new StringBuilder(2000);
        builder
            .append(LEVEL_TO_EMOTE.getOrDefault(event.getLevel(), UNKNOWN_EMOTE));
        builder
            .append(" [**")
            .append(event.getLoggerName());
        if (event.getMarker() != null) {
            builder
                .append("**/**")
                .append(event.getMarker().getName());
        }
        builder
            .append("**] - ")
            .append(getFormattedMessage(event))
            .append(CoreConstants.LINE_SEPARATOR);

        if (event.getThrowableProxy() != null) {
            final var t = event.getThrowableProxy();
            builder
                .append(t.getClassName())
                .append(": ")
                .append(t.getMessage())
                .append(CoreConstants.LINE_SEPARATOR);

            final StringBuilder stacktrace = buildStacktrace(t);
            String stacktraceCutoff = null;
            builder.append("Stacktrace: ");
            if (stacktrace.length() > MAXIMUM_STACKTRACE_LENGTH) {
                stacktraceCutoff = stacktrace.substring(MAXIMUM_STACKTRACE_LENGTH, stacktrace.length());
                stacktrace.delete(MAXIMUM_STACKTRACE_LENGTH, stacktrace.length());
            }

            builder.append(CoreConstants.LINE_SEPARATOR)
                .append("```ansi")
                .append(CoreConstants.LINE_SEPARATOR)
                .append(stacktrace)
                .append("```");

            if (stacktraceCutoff != null) {
                builder.append("*Too long to fully display. ")
                    .append(stacktraceCutoff.length())
                    .append(" characters or ")
                    .append(stacktraceCutoff.lines().count())
                    .append(" lines were truncated.*");
            }
        }
        return builder.toString();
    }

    private StringBuilder buildStacktrace(IThrowableProxy exception) {
        final var builder = new StringBuilder();
        for (int i = 0; i < exception.getStackTraceElementProxyArray().length; i++) {
            builder.append("\t ").append(exception.getStackTraceElementProxyArray()[i].toString())
                .append(CoreConstants.LINE_SEPARATOR);
        }
        return builder;
    }

    /**
     * Converts the given {@link ILoggingEvent} into a formatted message string, converting {@link IMentionable}s
     * as needed.
     *
     * @param event The logging event
     * @return The formatted message, with replaced mentions
     * @see #tryFormat(Object) #tryConvertMentionables(Object)
     */
    private String getFormattedMessage(final ILoggingEvent event) {
        final Object[] arguments = event.getArgumentArray();
        if (event.getArgumentArray() != null) {
            var newArgs = new Object[arguments.length];
            for (var i = 0; i < arguments.length; i++) {
                newArgs[i] = tryFormat(arguments[i]);
            }

            return MessageFormatter.arrayFormat(event.getMessage(), newArgs).getMessage();
        }
        return event.getFormattedMessage();
    }
}
