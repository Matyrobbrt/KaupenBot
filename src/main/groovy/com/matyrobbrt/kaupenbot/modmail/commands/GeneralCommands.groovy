package com.matyrobbrt.kaupenbot.modmail.commands

import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.modmail.ModMail
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

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