package com.matyrobbrt.kaupenbot.extensions

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.MarkdownUtil
import org.jetbrains.annotations.Nullable

import javax.annotation.Nonnull
import java.awt.*

@CompileStatic
@RegisterExtension
final class MiscExtension implements BotExtension {
    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(MessageReceivedEvent) {
            if (!fromGuild) return
            final var originalMsg = getMessage()
            if (originalMsg.getMessageReference() != null && isStringReference(originalMsg.getContentRaw())) {
                final var referencedMessage = originalMsg.getMessageReference().getMessage()
                if (referencedMessage != null) {
                    channel.sendMessageEmbeds(reference(referencedMessage, getMember()))
                        .flatMap { originalMsg.delete().reason('Message was a reference') }
                        .queue()
                    return
                }
            }

            final msg = originalMsg.getContentRaw().split(' ')
            if (msg.length < 1 || msg[0].startsWith('<')) { // Ignore `<link>` as Discord removes the embed for those links
                return
            }

            getJDA().getMessageByLink(msg[0])?.queue(message -> {
                channel.sendMessageEmbeds(reference(message, getMember()))
                    .flatMap({ msg.length === 1 && originalMsg.messageReference === null })
                            { originalMsg.delete().reason('Message was a reference') }
                    .queue()
            }, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE))
        }
    }

    public static final String ZERO_WIDTH_SPACE = String.valueOf('\u200E');

    private static boolean isStringReference(@Nonnull final String string) {
        return string == '.' || string == '^' || string == ZERO_WIDTH_SPACE
    }

    static MessageEmbed reference(@Nonnull final Message message, @Nullable final Member quoter) {
        reference(message, new Quoter(quoter.idLong, quoter.user.asTag, quoter.effectiveAvatarUrl))
    }

    static MessageEmbed reference(@Nonnull final Message message, @Nullable final Quoter quoter) {
        final var hasAuthor = !message.webhookMessage
        final var msgLink = message.jumpUrl
        final var embed = new EmbedBuilder().setTimestamp(message.getTimeCreated())
                .setColor(Color.DARK_GRAY)
        if (hasAuthor) {
            embed.setAuthor(message.getAuthor().getAsTag(), msgLink, message.getAuthor().getEffectiveAvatarUrl())
        }
        if (!message.getContentRaw().isBlank()) {
            embed.appendDescription(MarkdownUtil.maskedLink('Reference ➤ ', msgLink))
                    .appendDescription(message.getContentRaw());
        } else {
            embed.appendDescription(MarkdownUtil.maskedLink('Jump to referenced message.', msgLink))
        }
        if (quoter !== null && quoter.id !== message.getAuthor().getIdLong()) {
            embed.setFooter(quoter.tag + ' referenced', quoter.getAvatarUrl())
        }
        if (!message.attachments.isEmpty()) {
            embed.setImage(message.attachments[0].url)
        }
        return embed.build()
    }

    @CompileStatic
    static final class Quoter {
        final long id
        final String tag
        final String avatarUrl

        Quoter(long id, String tag, String avatarUrl) {
            this.id = id
            this.tag = tag
            this.avatarUrl = avatarUrl
        }
    }
}
