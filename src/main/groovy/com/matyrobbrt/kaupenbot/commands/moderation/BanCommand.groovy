package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.common.util.TimeUtils
import com.matyrobbrt.kaupenbot.extensions.logging.ModLogs
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.time.Duration
import java.util.concurrent.TimeUnit

// TODO hierarchy checks
@CompileStatic
final class BanCommand extends SlashCommand {
    BanCommand() {
        name = 'ban'
        help = 'Bans an user'
        userPermissions = new Permission[] { Permission.BAN_MEMBERS }
        options = [
                new OptionData(OptionType.USER, 'user', 'The user to ban', true),
                new OptionData(OptionType.STRING, 'reason', 'The ban reason', true),
                new OptionData(OptionType.STRING, 'deletion', 'The timeframe to delete messages')
        ]
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final reason = event.string('reason')
        final user = event.member('user')
        final Duration deletion = TimeUtils.getDurationFromInput(event.string('deletion', '0d'))

        ModLogs.putData(ActionType.BAN, user.idLong, event.user.idLong, reason)
        user.ban(deletion.toSeconds() as int, TimeUnit.SECONDS)
            .reason("Ban issued by ${event.user.id}: $reason")
            .flatMap { event.reply("🔨 Banned `${user.user.asTag}`.\n**Reason**: $reason") }
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
                it.ban(0, TimeUnit.DAYS)
                    .reason("Ban issued by ${event.author.id}: $reason")
            }
            .flatMap { event.reply("🔨 Banned `${toBan.asTag}`.\n**Reason**: $reason") }
            .queue()
    }
}
