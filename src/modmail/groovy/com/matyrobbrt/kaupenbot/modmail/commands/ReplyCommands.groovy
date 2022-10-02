//file:noinspection DuplicatedCode
package com.matyrobbrt.kaupenbot.modmail.commands

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender
import com.matyrobbrt.kaupenbot.modmail.ModMail
import com.matyrobbrt.kaupenbot.modmail.ModMailListener
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.awt.*

@CompileStatic
final class ReplyCommand extends SlashCommand {
    ReplyCommand() {
        name = 'reply'
        help = 'Reply to the ticket.'
        options = [
                new OptionData(OptionType.STRING, 'message', 'The message to reply', true)
        ]
        userPermissions = new Permission[] {
            Permission.MODERATE_MEMBERS
        }
        guildOnly = true
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        ReplyCommands.sendSlash(event, false)
    }

    @Override
    protected void execute(CommandEvent event) {
        ReplyCommands.sendPrefix(event, false)
    }
}

@CompileStatic
final class AReplyCommand extends SlashCommand {
    AReplyCommand() {
        name = 'areply'
        help = 'Reply anonymously to the ticket.'
        options = [
                new OptionData(OptionType.STRING, 'message', 'The message to reply', true)
        ]
        userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        }
        guildOnly = true
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        ReplyCommands.sendSlash(event, true)
    }

    @Override
    protected void execute(CommandEvent event) {
        ReplyCommands.sendPrefix(event, true)
    }
}

@CompileStatic
static void sendPrefix(CommandEvent event, boolean isAnonymous) {
    if (event.channel.type !== ChannelType.GUILD_PUBLIC_THREAD) return
    final thread = event.channel as ThreadChannel
    if (thread.parentChannel.idLong !== ModMail.config.loggingChannel) return

    final reference = event.message.messageReference?.with { ref ->
        ModMail.database.withExtension(TicketsDAO) {
            it.getAssociatedMessage(ref.messageIdLong)
        }
    }

    ModMailListener.doReply(thread, event.member, isAnonymous, reference, event.args, event.message.attachments, {
        event.message.addReaction(ModMailListener.SUCCESS_EMOJI).queue()
    }, {
        event.message.reply(it).flatMap { event.message.addReaction(ModMailListener.FAILED_EMOJI) }
    })
}

@CompileStatic
static void sendSlash(SlashCommandEvent event, boolean isAnonymous) {
    if (event.channel.type !== ChannelType.GUILD_PUBLIC_THREAD) {
        event.reply('This command can only be used in threads!').queue()
        return
    }
    final thread = event.channel.asThreadChannel()
    if (thread.parentChannel.idLong !== ModMail.config.loggingChannel) {
        event.reply('This command can only be used in ticket threads!').queue()
        return
    }

    final text = event.getOption('message')?.asString
    ModMailListener.doReply(thread, event.member, isAnonymous, null, text, java.util.List.of(), {
        event.reply('Successfully sent message!').setEphemeral(true).queue()
        WebhookMessageSender.send(
                ModMailListener.WEBHOOKS[thread],
                embed {
                    sentNow()
                    color = Color.RED
                    description = text
                    title = 'Message Sent'
                    footer = 'Moderator ID: ' + event.member.id
                },
                event.user.asTag + (isAnonymous ? ' (anonymously)' : ''),
                event.member.effectiveAvatarUrl
        )
    }, {
        event.reply('Could not send message: ' + it).setEphemeral(true).queue()
    }, true)
}