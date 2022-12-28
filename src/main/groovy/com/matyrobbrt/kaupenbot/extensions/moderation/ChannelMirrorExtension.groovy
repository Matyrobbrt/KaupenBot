package com.matyrobbrt.kaupenbot.extensions.moderation

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookManager
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.apache.commons.collections4.BidiMap
import org.apache.commons.collections4.bidimap.DualHashBidiMap

import java.util.concurrent.TimeUnit

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'channelMirror')
class ChannelMirrorExtension implements BotExtension {
    private static final WebhookManager WEBHOOKS = WebhookManager.of('ChannelMirrors')

    private final BidiMap<MessageChannel, MessageChannel> channelToMirror = new DualHashBidiMap<>()
    private final Cache<Long, Long> mirroredMessageToParent = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'mirror'
            require = Permission.MANAGE_CHANNEL

            subCommand {
                name = 'set'
                description = 'Mirrors the channel to another channel.'
                options = [new OptionData(OptionType.CHANNEL, 'mirror', 'The channel to mirror to.', true)]
                failIf({ channelToMirror.containsKey(it.channel) }, 'The channel is already mirrored to another channel.')
                failIf({ channelToMirror.containsValue(it.getOption('mirror').asChannel) }, 'The target channel already mirrors another channel.')
                action {
                    final target = getOption('mirror').asChannel.asGuildMessageChannel()
                    channelToMirror.put(it.channel, target)
                    target.sendMessage("This channel has been linked to ${channel.asMention}!")
                            .flatMap { _ -> it.replyEphemeral('Channels have been linked!') }
                            .queue()
                }
            }
            subCommand {
                name = 'remove'
                description = 'Unlinks a channel mirror.'
                checkIf({ channelToMirror.containsKey(it.channel) }, 'The channel is not mirrored to another channel.')
                action {
                    channelToMirror.remove(it.channel)
                    replyEphemeral('Channels have been unlinked!').queue()
                }
            }
        }
    }

    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(MessageReceivedEvent) {
            if (!it.fromGuild || it.message.author.bot) return

            final mirror = channelToMirror.get(it.channel)
            if (mirror !== null) {
                WebhookMessageSender.proxy(WEBHOOKS.getWebhook(mirror), it.message)
                        .thenAccept { msg ->
                    mirroredMessageToParent.put(msg.id, it.messageIdLong)
                }.exceptionHandling()
            } else {
                final parent = channelToMirror.getKey(it.channel)
                if (parent !== null) {
                    final action = parent.sendMessage(new MessageCreateBuilder().applyMessage(it.message).setFiles(it.message.attachments
                            .stream().map { FileUpload.fromData(WebhookMessageSender.readBytes(it), it.fileName) }.toList()).build())
                    if (it.message.messageReference !== null) {
                        action.setMessageReference(mirroredMessageToParent.getIfPresent(it.message.messageReference.messageIdLong))
                    }
                    action.queue()
                }
            }
        }

        jda.subscribe(MessageReactionAddEvent) {
            final parentMsgId = mirroredMessageToParent.getIfPresent(it.messageIdLong)
            if (parentMsgId === null) return
            channelToMirror.getKey(it.channel)?.addReactionById(parentMsgId, it.emoji)?.queue()
        }

        jda.subscribe(MessageReactionRemoveEvent) {
            final parentMsgId = mirroredMessageToParent.getIfPresent(it.messageIdLong)
            if (parentMsgId === null) return
            channelToMirror.getKey(it.channel)?.removeReactionById(parentMsgId, it.emoji)?.queue()
        }
    }
}
