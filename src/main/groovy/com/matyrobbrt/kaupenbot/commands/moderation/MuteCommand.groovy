package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.common.util.TimeUtils
import com.matyrobbrt.kaupenbot.extensions.logging.ModLogs
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.TimeFormat

import java.time.Instant

// TODO hierarchy checks
@CompileStatic
final class MuteCommand extends SlashCommand {
    MuteCommand() {
        name = 'mute'
        help = 'Mute an user'
        userPermissions = new Permission[] { Permission.MODERATE_MEMBERS }
        options = [
                new OptionData(OptionType.USER, 'user', 'The user to ban', true),
                new OptionData(OptionType.STRING, 'reason', 'The mute reason', true),
                new OptionData(OptionType.STRING, 'duration', 'The mute duration', true)
        ]
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final reason = event.string('reason')
        final user = event.member('user')
        final duration = TimeUtils.getDurationFromInput(event.string('duration'))
        if (duration.toHours() > Member.MAX_TIME_OUT_LENGTH * 24) {
            event.replyProhibited('Cannot time out for more than 28 days!').queue()
            return
        }

        final toRun = { boolean didDm ->
            ModLogs.putData(ActionType.MEMBER_UPDATE, user.idLong, event.user.idLong, reason)
            user.timeoutFor(duration)
                    .reason("Mute issued by ${event.user.id}: $reason")
                    .flatMap {
                        final action = event.reply("🔇 Muted `${user.user.asTag}`.\n**Reason**: $reason")
                        if (!didDm) action.addContent('\n*User could not be messaged!*')
                        return action
                    }
                    .queue()
        }
        user.user.openPrivateChannel()
                .flatMap { it.sendMessage("You have been 🔇 **muted** in **${event.guild.name}!\n**Reason**: $reason\n**Timeout end**: ${TimeFormat.RELATIVE.format(Instant.now() + duration)}") }
                .queue({ toRun(true) }, { toRun(false) })
    }

    @Override
    protected void execute(CommandEvent event) {
        final split = event.args.split(' ')
        final toMute = event.message.mentionedUser(split)
        final duration = TimeUtils.getDurationFromInput(split[1])
        if (duration.toHours() > Member.MAX_TIME_OUT_LENGTH * 24) {
            event.message.reply('🚫 Cannot time out for more than 28 days!').queue()
            return
        }

        final reason = split.drop(2).join(' ')

        final toRun = { boolean didDm ->
            ModLogs.putData(ActionType.MEMBER_UPDATE, toMute.idLong, event.author.idLong, reason)
            event.guild.retrieveMemberById(toMute.idLong)
                    .flatMap {
                        it.timeoutFor(duration).reason("Mute issued by ${event.author.id}: $reason")
                    }
                    .flatMap {
                        final action = event.message.reply("🔇 Muted `${toMute.asTag}`.\n**Reason**: $reason")
                        if (!didDm) action.addContent('\n*User could not be messaged!*')
                        return action
                    }
                    .queue()
        }

        toMute.openPrivateChannel()
                .flatMap { it.sendMessage("You have been 🔇 **muted** in **${event.guild.name}!\n**Reason**: $reason\n**Timeout end**: ${TimeFormat.RELATIVE.format(Instant.now() + duration)}") }
                .queue({ toRun(true) }, { toRun(false) })
    }
}
