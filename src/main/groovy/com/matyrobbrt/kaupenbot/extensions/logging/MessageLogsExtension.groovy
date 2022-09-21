//file:noinspection GrMethodMayBeStatic
package com.matyrobbrt.kaupenbot.extensions.logging

import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.caching.JdaMessageCache
import com.matyrobbrt.kaupenbot.common.util.caching.MessageData
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookManager
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.StandardGuildMessageChannel
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent

import java.awt.*

@CompileStatic
@RegisterExtension(value = 'messageLogs', botId = 'kbot')
final class MessageLogsExtension implements BotExtension {
    private static final WebhookManager WEBHOOKS = WebhookManager.of('MessageLogs')

    public static final Color GRAY_CHATEAOU = new Color(0x979C9F);
    public static final Color VIVID_VIOLET = new Color(0x71368A);

    @Override
    void subscribeEvents(JDA jda) {
        // TODO log attachments
        final logs = JdaMessageCache.builder()
            .onDelete(this.&onMessageDelete)
            .onEdit(this.&onMessageUpdate)
            .build()

        jda.addEventListener(logs)
    }

    void onMessageDelete(final MessageDeleteEvent event, final MessageData data) {
        if (!event.fromGuild || (data.content.blank && data.attachments.empty)) return
        final var loggingChannel = KaupenBot.config.loggingChannels.messageLogs
        if (loggingChannel.is(0L) || event.channel.idLong.is(loggingChannel) || event.channel.idLong in KaupenBot.config.channels.loggingIgnored) return
        final var msgSplit = data.getContent().split(" ")
        if (msgSplit.length == 1) {
            final var matcher = Message.JUMP_URL_PATTERN.matcher(msgSplit[0])
            if (matcher.find()) {
                return
            }
        }
        final var embedBuilder = new EmbedBuilder()
        embedBuilder.setColor(GRAY_CHATEAOU)
                .setDescription("**A message sent by <@${data.authorId}> in <#${data.channelId}> has been deleted!**\n${data.content.truncate(MessageEmbed.DESCRIPTION_MAX_LENGTH - 90)}")
        embedBuilder.sentNow()
                .setFooter("Author: $data.authorId | Message ID: ${event.channel.id}", null)
        final var interaction = data.getInteraction()
        if (interaction !== null) {
            embedBuilder.addField("Interaction Author: ", "<@$interaction.authorId> ($interaction.authorId)", true)
        }
        if (!data.attachments.isEmpty()) {
            embedBuilder.setImage(data.attachments[0])
        }

        WebhookMessageSender.send(
                WEBHOOKS[event.getJDA().getChannelById(StandardGuildMessageChannel, loggingChannel)],
                embedBuilder.build(), data.authorUsername, data.authorAvatar
        )
    }

    void onMessageUpdate(final MessageUpdateEvent event, MessageData data) {
        final var newMessage = event.getMessage()
        if (!event.fromGuild || (newMessage.getContentRaw().isBlank() && newMessage.getAttachments().isEmpty()))
            return
        final var loggingChannel = KaupenBot.config.loggingChannels.messageLogs
        if (loggingChannel.is(0L) || event.channel.idLong.is(loggingChannel) || event.channel.idLong in KaupenBot.config.channels.loggingIgnored) return
        final var embedBuilder = new EmbedBuilder()
        embedBuilder.setColor(VIVID_VIOLET)
                .setDescription("**A message sent by <@$data.authorId> in <#${event.channel.id}> has been edited!** [Jump to message.]($newMessage.jumpUrl)")
        embedBuilder.sentNow()
        embedBuilder.addField("Before", data.getContent().isBlank() ? "*Blank*" : data.content.truncate(MessageEmbed.VALUE_MAX_LENGTH), false)
                .addField("After", newMessage.getContentRaw().isBlank() ? "*Blank*" : newMessage.contentRaw.truncate(MessageEmbed.VALUE_MAX_LENGTH), false)
        embedBuilder.setFooter("Author ID: " + data.getAuthorId(), null)
        final var interaction = data.getInteraction()
        if (interaction !== null) {
            embedBuilder.addField("Interaction Author: ", "<@$interaction.authorId> ($interaction.authorId)", true)
        }
        if (!data.attachments.empty) {
            embedBuilder.setImage(data.getAttachments()[0])
        }

        WebhookMessageSender.send(
                WEBHOOKS[event.getJDA().getChannelById(StandardGuildMessageChannel, loggingChannel)],
                embedBuilder.build(), data.authorUsername, data.authorAvatar
        )
    }
}
