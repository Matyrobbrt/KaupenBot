package com.matyrobbrt.kaupenbot.modmail.commands

import com.jagrosh.jdautilities.command.SlashCommand
import com.matyrobbrt.kaupenbot.modmail.ModMail
import com.matyrobbrt.kaupenbot.modmail.ModMailListener
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import com.matyrobbrt.kaupenbot.util.CallbackCommand
import com.matyrobbrt.kaupenbot.util.webhooks.WebhookMessageSender
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

import java.awt.*

@CompileStatic
final class OpenTicketCommand extends SlashCommand implements CallbackCommand {
    OpenTicketCommand() {
        name = 'open-ticket'
        help = 'Opens a ticket with an user'
        userPermissions = new Permission[]{Permission.MODERATE_MEMBERS}
        options.add(new OptionData(OptionType.USER, 'user', 'The user to open a ticket with.', true))
        options.add(new OptionData(OptionType.STRING, 'message', 'The message to send to the user.'))
        setCallback {
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
}
