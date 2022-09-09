//file:noinspection DuplicatedCode
package com.matyrobbrt.kaupenbot.modmail.commands

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.modmail.ModMail
import com.matyrobbrt.kaupenbot.modmail.ModMailListener
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.ThreadChannel
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageEditData

import java.awt.*
import java.util.function.Function

@CompileStatic
final class CloseCommand extends SlashCommand {

    CloseCommand() {
        name = 'close'
        help = 'Close the ticket.'
        options = [
                new OptionData(OptionType.STRING, 'reason', 'The reason for closing the ticket.', true)
        ]
        userPermissions = new Permission[]{
                Permission.MODERATE_MEMBERS
        }
        guildOnly = true
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        CloseCommands.sendSlash(event, false)
    }

    @Override
    protected void execute(CommandEvent event) {
        CloseCommands.sendPrefix(event, false)
    }
}

@CompileStatic
final class ACloseCommand extends SlashCommand {

    ACloseCommand() {
        name = 'aclose'
        help = 'Close the anonymously ticket.'
        options = [
                new OptionData(OptionType.STRING, 'reason', 'The reason for closing the ticket.', true)
        ]
        userPermissions = new Permission[]{
                Permission.MODERATE_MEMBERS
        }
        guildOnly = true
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        CloseCommands.sendSlash(event, true)
    }

    @Override
    protected void execute(CommandEvent event) {
        CloseCommands.sendPrefix(event, true)
    }
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

    final reason = event.getOption('reason')?.asString
    close(thread, event.member, isAnonymous, reason, {
        event.reply('Successfully closed the ticket!').queue()
    }, {
        event.reply('Could not close ticket: ' + it).setEphemeral(true).queue()
    }, true)
}

@CompileStatic
static void sendPrefix(CommandEvent event, boolean isAnonymous) {
    if (event.channel.type !== ChannelType.GUILD_PUBLIC_THREAD) return
    final thread = event.channel as ThreadChannel
    if (thread.parentChannel.idLong !== ModMail.config.loggingChannel) return
    close(thread, event.member, isAnonymous, event.args, {}, {
        event.message.reply(it).flatMap { event.message.addReaction(ModMailListener.FAILED_EMOJI) }
    })
}

@CompileStatic
static void close(ThreadChannel thread, Member moderator, boolean anonymous, String reason, Runnable onSuccess, Function<String, RestAction> onFailure, boolean checkGoodThread = false) {
    final userId = ModMail.database.withExtension(TicketsDAO) {
        it.getUser(thread.idLong, true)
    }
    if (userId === null) {
        if (checkGoodThread) {
            onFailure.apply('This thread is not a ticket!').queue()
        }
        return
    }
    moderator.guild.retrieveMemberById(userId)
            .flatMap { it.user.openPrivateChannel() }
            .flatMap { ch ->
                ch.sendMessageEmbeds(embed {
                    color = Color.RED
                    title = 'Ticket closed'
                    if (anonymous) {
                        setAuthor('Anonymous')
                    } else {
                        setAuthor(moderator.user.asTag, null, moderator.user.effectiveAvatarUrl)
                    }
                    description = reason
                    setFooter("${moderator.guild.name} | ${moderator.guild.id}", moderator.guild.iconUrl)
                }).map { ch }
            }
            .flatMap { ch ->
                thread.retrieveParentMessage().flatMap {
                    it.editMessage(
                            MessageEditData.fromEmbeds(embed {
                                sentNow()
                                color = Color.GREEN
                                title = '~~New Ticket~~ Ticked has been closed'
                                description = "Ticked has been closed by $moderator.asMention. Reason: $reason"
                                setFooter("${ch.user.asTag} | ${ch.user.id}", ch.user.effectiveAvatarUrl)
                            })
                    )
                }
            }
            .flatMap {
                thread.sendMessage('This thread will now be archived! Please refrain from sending messages in it!')
            }
            .map { onSuccess.run(); return it }
            .flatMap { thread.manager.setArchived(true).setLocked(true) }
            .queue({
                ModMail.database.useExtension(TicketsDAO) {
                    it.markActive(thread.idLong, false)
                }
            }, new ErrorHandler()
                    .handle(ErrorResponse.CANNOT_SEND_TO_USER, {
                        onFailure.apply('Cannot send messages to this user!\nPlease contact them manually and ask them to open their DMs.').queue()
                    })
                    .ignore(ErrorResponse.UNKNOWN_MEMBER))
}