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

        final toRun = { boolean didDm ->
            ModLogs.putData(ActionType.KICK, user.idLong, event.user.idLong, reason)
            user.kick()
                    .reason("Kick issued by ${event.user.id}: $reason")
                    .flatMap {
                        final action = event.reply("👢 Kicked `${user.user.asTag}`.\n**Reason**: $reason")
                        if (!didDm) action.addContent('\n*User could not be messaged!*')
                        return action
                    }
                    .queue()
        }
        user.user.openPrivateChannel()
                .flatMap { it.sendMessage("You have been 👢 **kicked** in **${event.guild.name}!\n**Reason**: $reason") }
                .queue({ toRun(true) }, { toRun(false) })
    }

    @Override
    protected void execute(CommandEvent event) {
        final split = event.args.split(' ')
        final toKick = event.message.mentionedUser(split)
        final reason = split.drop(1).join(' ')

        final toRun = { boolean didDm ->
            ModLogs.putData(ActionType.KICK, toKick.idLong, event.author.idLong, reason)
            event.guild.retrieveMemberById(toKick.idLong)
                    .flatMap {
                        it.kick().reason("Kick issued by ${event.author.id}: $reason")
                    }
                    .flatMap {
                        final action = event.message.reply("👢 Kicked `${toKick.asTag}`.\n**Reason**: $reason")
                        if (!didDm) action.addContent('\n*User could not be messaged!*')
                        return action
                    }
                    .queue()
        }
        toKick.openPrivateChannel()
                .flatMap { it.sendMessage("You have been 👢 **kicked** in **${event.guild.name}!\n**Reason**: $reason") }
                .queue({ toRun(true) }, { toRun(false) })
    }
}
