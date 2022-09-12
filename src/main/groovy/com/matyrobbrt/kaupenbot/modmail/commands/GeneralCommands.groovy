package com.matyrobbrt.kaupenbot.modmail.commands
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.kaupenbot.modmail.ModMail
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import com.matyrobbrt.kaupenbot.util.PaginatedSlashCommand
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ThreadChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.TimeFormat

import java.awt.*
import java.util.List

@CompileStatic
final class BlackListCommand extends SlashCommand {

    BlackListCommand() {
        name = 'blacklist'
        help = 'Blacklist a user from tickets.'
        options = [
                new OptionData(OptionType.USER, 'user', 'The user to blacklist.', true)
        ]
        userPermissions = [Permission.MODERATE_MEMBERS]
        botPermissions = [Permission.MANAGE_ROLES]
        guildOnly = true
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final user = event.getOption('user')?.asMember
        if (user === null) {
            event.reply('Unknown user!').setEphemeral(true).queue()
            return
        }
        if (user.roles.any { it.idLong == ModMail.config.blacklistedRole }) {
            event.reply('User is blacklisted!').setEphemeral(true).queue()
            return
        }

        event.guild.addRoleToMember(user, event.guild.getRoleById(ModMail.config.blacklistedRole))
            .flatMap { event.reply("Blacklisted $user.asMention!") }
            .queue()
    }
}
@CompileStatic
final class UnBlackListCommand extends SlashCommand {

    UnBlackListCommand() {
        name = 'unblacklist'
        help = 'Un-blacklist a user from tickets.'
        options = [
                new OptionData(OptionType.USER, 'user', 'The user to un-blacklist.', true)
        ]
        userPermissions = [Permission.MODERATE_MEMBERS]
        botPermissions = [Permission.MANAGE_ROLES]
        guildOnly = true
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final user = event.getOption('user')?.asMember
        if (user === null) {
            event.reply('Unknown user!').setEphemeral(true).queue()
            return
        }
        if (user.roles.none { it.idLong == ModMail.config.blacklistedRole }) {
            event.reply('User is not blacklisted!').setEphemeral(true).queue()
            return
        }

        event.guild.removeRoleFromMember(user, event.guild.getRoleById(ModMail.config.blacklistedRole))
                .flatMap { event.reply("Un-blacklisted $user.asMention!") }
                .queue()
    }
}

@CompileStatic
@Newify([OptionData, EmbedBuilder])
final class TicketsCommand extends PaginatedSlashCommand {

    TicketsCommand() {
        super(ModMail.paginator('tickets-cmd')
                .buttonsOwnerOnly(true)
                .buttonOrder(Paginator.DEFAULT_BUTTON_ORDER)
                .itemsPerPage(10), true)
        name = 'tickets'
        guildOnly = true
        help = 'List all tickets a user has had.'
        userPermissions = [Permission.MODERATE_MEMBERS]
        options = [
                OptionData(OptionType.USER, 'user', 'The user whose tickets to list.', true)
        ]
    }

    @Override
    protected EmbedBuilder getEmbed(int startingIndex, int maximum, List<String> arguments) {
        final userId = arguments[0] as long
        final tickets = ModMail.database.withExtension(TicketsDAO) { it.getThreads(userId) }
        final threads = ModMail.guild.getTextChannelById(ModMail.config.loggingChannel).retrieveArchivedPublicThreadChannels().submit().get()
        return EmbedBuilder().tap {
            sentNow()
            color = Color.RED
            description = "<@${userId}>'s tickets:\n"
            tickets.drop(startingIndex).take(itemsPerPage)
                .forEach {
                    ThreadChannel thread = ModMail.guild.getThreadChannelById(it)
                    if (thread === null)
                        thread = threads.find { id -> id.idLong == it }
                    if (thread != null) {
                        appendDescription("$thread.asMention - ${TimeFormat.DATE_TIME_SHORT.format(thread.timeCreated)}. Open: ${thread.archived}\n")
                    }
                }
            description = descriptionBuilder.toString().trim()
            footer = "Page ${getPageNumber(startingIndex)} / ${getPagesNumber(maximum)}"
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final user = event.getOption('user')?.asUser
        if (user === null) {
            event.reply('Unknown user!').setEphemeral(true).queue()
            return
        }

        final tickets = ModMail.database.withExtension(TicketsDAO) { it.getThreads(user.idLong) }
        createPaginatedMessage(event, tickets.size(), user.id).queue()
    }
}