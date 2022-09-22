package com.matyrobbrt.kaupenbot.extensions

import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.common.util.TimeUtils
import com.sigpwned.emoji4j.core.GraphemeMatcher
import com.sun.net.httpserver.HttpExchange
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

@CompileStatic
class Extensions {
    static EmbedBuilder sentNow(EmbedBuilder self) {
        return self.setTimestamp(Instant.now())
    }

    static <T> boolean none(Iterator<T> self, @ClosureParams(FirstParam.FirstGenericType.class) Closure predicate) {
        BooleanClosureWrapper bcw = new BooleanClosureWrapper(predicate)
        while (self.hasNext()) {
            if (bcw.call(self.next())) return false
        }
        return true
    }
    static <T> boolean none(Iterable<T> self, @ClosureParams(FirstParam.FirstGenericType.class) Closure predicate) {
        return none(self.iterator(), predicate)
    }

    @NotNull
    static String string(final SlashCommandInteractionEvent event, final String name, final String defaultValue = '') {
        return event.getOption(name)?.asString ?: defaultValue
    }
    @Nullable
    static User user(final SlashCommandInteractionEvent event, final String name) {
        event.getOption(name)?.asUser
    }
    @Nullable
    static Role role(final SlashCommandInteractionEvent event, final String name) {
        event.getOption(name)?.asRole
    }
    @Nullable
    static Member member(final SlashCommandInteractionEvent event, final String name) {
        event.getOption(name)?.asMember
    }

    static int integer(final SlashCommandInteractionEvent event, final String name, final int defaultValue = 1) {
        final opt = event.getOption(name)
        if (opt === null) return defaultValue
        return opt.asInt
    }

    @org.jetbrains.annotations.Nullable
    static <T extends Enum<T>> T enumOption(final SlashCommandInteractionEvent event, final Class<T> clazz, final String name) {
        final ename = event.getOption(name)?.asString
        if (ename === null) return null
        Enum.valueOf(clazz, ename)
    }

    @Nullable
    static Member getAt(Guild self, @Nonnull UserSnowflake user) {
        self.retrieveMember(user).submit(true).get()
    }

    static ReplyCallbackAction replyProhibited(IReplyCallback self, String message) {
        self.reply("⛔ $message").setEphemeral(true)
    }
    static ReplyCallbackAction replyEphemeral(IReplyCallback self, String message) {
        self.reply(message).setEphemeral(true)
    }

    /**
     * Disables all the buttons that a message has. Disabling buttons deems it as not clickable to
     * the user who sees it.
     * <p>
     * This method already queues the changes for you and does not block in any way.
     *
     * @param message the message that contains at least one button
     * @throws IllegalArgumentException when the given message does not contain any action row
     */
    static void disableButtons(Message self) {
        if (self.actionRows.empty) {
            throw new IllegalArgumentException('Message must contain at least one action row!')
        }
        final List<ActionRow> newRows = new ArrayList<>(self.actionRows.size())
        for (final row : self.getActionRows()) {
            newRows.add(ActionRow.of(row.getComponents().stream().map((item) -> item instanceof Button ? item.asDisabled() : item)
                    .toList()))
        }

        self.editMessageComponents(newRows)
                .queue()
    }

    static <Z extends Enum<Z>> OptionData addEnum(OptionData self, Class<Z> enumType) {
        self.addChoices(Arrays.stream(enumType.enumConstants).map { new Command.Choice(it.toString(), it.name()) }.toList())
    }

    static Duration getAsDuration(OptionMapping self) {
        return TimeUtils.getDurationFromInput(self.asString)
    }

    @org.jetbrains.annotations.Nullable
    static RestAction<Message> getMessageByLink(JDA self, String link) {
        return StaticExtensions.decodeMessageLink(null, link)
            .map {
                self.getGuildById(it.guildId())?.getChannelById(MessageChannel, it.channelId())?.retrieveMessageById(it.messageId())
            }
            .orElse(null)
    }

    static void reply(HttpExchange exchange, String message, int code) throws IOException {
        //noinspection GrDeprecatedAPIUsage
        final resp = message.getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(code, resp.length)
        try (final os = exchange.responseBody) {
            os.write(resp)
        }
    }

    static Node getFirst(Node self, String key) {
        final list = self.get(key) as NodeList
        if (list.isEmpty()) return null
        return list.get(0) as Node
    }

    static String truncate(final String self, int limit) {
        return self.length() > (limit - 3) ? self.substring(0, limit - 3) + '...' : self
    }

    static List<Emoji> parseEmojis(String self) {
        final Set<Emoji> emojis = []

        final matcher = Message.MentionType.EMOJI.pattern.matcher(self)
        while (matcher.find()) {
            emojis.add(Emoji.fromFormatted(matcher.group()))
        }

        final graphemeMatcher = new GraphemeMatcher(self)
        while (graphemeMatcher.find()) {
            final match = graphemeMatcher.grapheme()
            emojis.add(Emoji.fromUnicode(match.toString()))
        }

        final list = new ArrayList<Emoji>(emojis)
        list.sort(Comparator.<Emoji>comparingInt {
            self.indexOf(it.formatted)
        })
        return list
    }
}
