package com.matyrobbrt.kaupenbot.extensions

import com.jagrosh.jdautilities.command.SlashCommandEvent
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull
import javax.annotation.Nullable
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
    static String string(final SlashCommandEvent event, final String name) {
        return event.getOption(name)?.asString ?: ''
    }
    @Nullable
    static User user(final SlashCommandEvent event, final String name) {
        return event.getOption(name)?.asUser
    }

    @Nullable
    static Member getAt(Guild self, @Nonnull UserSnowflake user) {
        self.retrieveMember(user).submit(true).get()
    }

    static ReplyCallbackAction replyProhibited(IReplyCallback self, String message) {
        self.reply("⛔ $message").setEphemeral(true)
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
}
