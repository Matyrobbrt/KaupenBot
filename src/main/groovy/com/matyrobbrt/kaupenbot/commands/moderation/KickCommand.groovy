package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.extensions.logging.ModLogs
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

// TODO hierarchy checks
@CompileStatic
final class KickCommand extends SlashCommand {
    KickCommand() {
        name = 'kick'
        help = 'Kick an user'
        userPermissions = new Permission[] { Permission.KICK_MEMBERS }
        options = [
                new OptionData(OptionType.USER, 'user', 'The user to kick', true),
                new OptionData(OptionType.STRING, 'reason', 'The kick reason', true)
        ]
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final reason = event.string('reason')
        final user = event.member('user')

        ModLogs.putData(ActionType.BAN, user.idLong, event.user.idLong, reason)
        user.kick()
            .reason("Kick issued by ${event.user.id}: $reason")
            .flatMap { event.reply("👢 Kicked `${user.user.asTag}`.\n**Reason**: $reason") }
            .queue()
    }

    @Override
    protected void execute(CommandEvent event) {
        final split = event.args.split(' ')
        final toBan = event.message.mentionedUser(split)
        final reason = split.drop(1).join(' ')

        ModLogs.putData(ActionType.BAN, toBan.idLong, event.author.idLong, reason)
        event.guild.retrieveMemberById(toBan.idLong)
            .flatMap {
                it.kick().reason("Kick issued by ${event.author.id}: $reason")
            }
            .flatMap { event.reply("👢 Kicked `${toBan.asTag}`.\n**Reason**: $reason") }
            .queue()
    }
}
