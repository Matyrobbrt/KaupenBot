package com.matyrobbrt.kaupenbot.modmail.commands

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender
import com.matyrobbrt.kaupenbot.modmail.ModMail
import com.matyrobbrt.kaupenbot.modmail.ModMailListener
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.TimeFormat
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

import java.awt.*
import java.util.List

@CompileStatic
@RegisterExtension(botId = 'modmail', value = 'commands')
final class CommandsExtension implements BotExtension {
    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        [new AReplyCommand(), new ReplyCommand(),
         new CloseCommand(), new ACloseCommand(),
        ].each {
            client.addCommand(it)
            client.addSlashCommand(it)
        }

        manager.addCommand {
            name = 'blacklist'
            description = 'Blacklist a user from tickets.'
            options = [
                    new OptionData(OptionType.USER, 'user', 'The user to blacklist.', true)
            ]
            require(Permission.MODERATE_MEMBERS)
            action {
                final user = getOption('user')?.asMember
                if (user === null) {
                    replyProhibited('Unknown user!').setEphemeral(true).queue()
                    return
                }
                if (user.roles.any { it.idLong == ModMail.config.blacklistedRole }) {
                    replyProhibited('User is blacklisted already!').setEphemeral(true).queue()
                    return
                }

                guild.addRoleToMember(user, guild.getRoleById(ModMail.config.blacklistedRole))
                        .flatMap { reply("Blacklisted $user.asMention!").setAllowedMentions(List.of()) }
                        .queue()
            }
        }

        manager.addCommand {
            name = 'unblacklist'
            description = 'Un-blacklist a user from tickets.'
            options = [
                    new OptionData(OptionType.USER, 'user', 'The user to un-blacklist.', true)
            ]
            require(Permission.MODERATE_MEMBERS)
            action {
                final user = getOption('user')?.asMember
                if (user === null) {
                    replyProhibited('Unknown user!').setEphemeral(true).queue()
                    return
                }
                if (user.roles.none { it.idLong == ModMail.config.blacklistedRole }) {
                    replyProhibited('User is not blacklisted!').setEphemeral(true).queue()
                    return
                }

                it.guild.removeRoleFromMember(user, it.guild.getRoleById(ModMail.config.blacklistedRole))
                        .flatMap { reply("Un-blacklisted $user.asMention!").setAllowedMentions(List.of()) }
                        .queue()
            }
        }

        manager.addCommand {
            name = 'open-ticket'
            description = 'Opens a ticket with an user'
            require(Permission.MODERATE_MEMBERS)
            options.add(new OptionData(OptionType.USER, 'user', 'The user to open a ticket with.', true))
            options.add(new OptionData(OptionType.STRING, 'message', 'The message to send to the user.'))

            action {
                final user = member('user')
                if (user === null) {
                    replyProhibited('Unknown user!').queue()
                    return
                }
                deferReply().queue()

                final active = ModMail.activeTicket(user.idLong)
                if (active !== null && ModMail.jda.getChannelById(MessageChannel, active) !== null) {
                    replyProhibited("This user already has an open ticket! <#$active>").queue()
                    return
                }

                final embed = new EmbedBuilder().tap {
                    sentNow()
                    color = Color.GREEN
                    title = 'New Ticket'
                    setFooter("${user.user.asTag} | ${user.user.id}", user.user.effectiveAvatarUrl)
                }
                ModMail.log(MessageCreateData.fromEmbeds(embed.build())).flatMap {
                    it.createThreadChannel("${user.user.name}’s ticket")
                }.flatMap { th ->
                    th.retrieveParentMessage().flatMap {
                        it.editMessage(MessageEditData.fromEmbeds(embed.tap {
                            description = "Ticket has been initiated by $member.asMention: $th.asMention"
                        }.build()))
                    }
                    .flatMap { th.sendMessage(ModMailListener.makeInitialMessage(user)) }
                    .flatMap { th.sendMessage("Ticket has been initiated by $member.asMention.") }
                    .flatMap { user.user.openPrivateChannel() }
                    .flatMap {
                        it.sendMessageEmbeds(new EmbedBuilder().tap {
                            color = Color.RED
                            description = "*This ticket has been initiated by moderators in **$guild.name**.*"
                            title = 'Message Received'
                            final msg = string('message')
                            if (!msg.isEmpty())
                                descriptionBuilder.append('\n').append(msg)
                            setFooter("${guild.name} | ${guild.id}", guild.iconUrl)
                        }.build())
                    }
                    .map { th }
                }.submit().thenCompose {
                    ModMail.database.useExtension(TicketsDAO) { dao ->
                        dao.insertThread(user.idLong, it.idLong, true)
                    }
                    WebhookMessageSender.send(ModMailListener.WEBHOOKS[it],
                            new EmbedBuilder().tap {
                                sentNow()
                                color = Color.RED
                                description = string('message')
                                title = 'Message Sent'
                                footer = 'Moderator ID: ' + member.id
                            }.build(),
                            member.user.name, member.user.effectiveAvatarUrl
                    )
                }.whenComplete { msg, t ->
                    if (t !== null) {
                        hook.editOriginal('There was an exception trying to create the ticket: ' + t.localizedMessage).queue()
                    } else {
                        hook.editOriginal("Successfully created ticket: <$msg.channelId>").queue()
                    }
                }.exceptionHandling()
            }
        }

        manager.addPaginatedCommand {
            name = 'tickets'
            guildOnly = true
            description = 'List all tickets a user has had.'
            require(Permission.MODERATE_MEMBERS)

            options = [
                    new OptionData(OptionType.USER, 'user', 'The user whose tickets to list.', true)
            ]
            checkIf({ it.getOption('user')?.asUser !== null }, 'Unknown user!')

            action {
                final userOpt = getOption('user').asUser
                final tickets = ModMail.database.withExtension(TicketsDAO) { it.getThreads(userOpt.idLong) }
                createPaginatedMessage(it, tickets.size(), userOpt.id).queue()
            }

            setPaginator(ModMail.paginator('tickets-cmd')
                    .buttonsOwnerOnly(true)
                    .buttonOrder(Paginator.DEFAULT_BUTTON_ORDER)
                    .itemsPerPage(10), true)

            embedFactory { start, max, args ->
                final userId = args[0] as long
                final tickets = ModMail.database.withExtension(TicketsDAO) { it.getThreads(userId) }
                final threads = ModMail.guild.getTextChannelById(ModMail.config.loggingChannel).retrieveArchivedPublicThreadChannels().submit().get()
                return new EmbedBuilder().tap {
                    sentNow()
                    color = Color.RED
                    description = "<@${userId}>'s tickets:\n"
                    for (final ticket : tickets.drop(start).take(paginator.itemsPerPage)) {
                        ThreadChannel thread = ModMail.guild.getThreadChannelById(ticket)
                        if (thread === null)
                            thread = threads.find { id -> id.idLong == ticket }
                        if (thread != null) {
                            appendDescription("$thread.asMention - ${TimeFormat.DATE_TIME_SHORT.format(thread.timeCreated)}. Open: ${!thread.archived}\n")
                        }
                    }
                    description = descriptionBuilder.toString().trim()
                    footer = "Page ${getPageNumber(start)} / ${getPagesNumber(max)}"
                }
            }
        }
    }
}
