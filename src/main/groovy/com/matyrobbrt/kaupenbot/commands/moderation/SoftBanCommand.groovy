package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.util.concurrent.TimeUnit

@CompileStatic
final class SoftBanCommand extends SlashCommand {
    SoftBanCommand() {
        name = 'softban'
        help = 'Bans and unbans a member, deleting their messages.'
        guildOnly = true
        userPermissions = new Permission[] {
            Permission.BAN_MEMBERS
        }
        options = [
                new OptionData(OptionType.USER, 'user', 'The user to soft-ban.', true),
                new OptionData(OptionType.INTEGER, 'days', 'The amount of days to delete messages for.').setRequiredRange(1, 7)
        ]
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final days = event.integer('days', 1)
        final user = event.member('user')
        if (user === null) {
            event.replyProhibited('Unknown member!').queue()
            return
        }
        event.deferReply().queue()
        user.ban(days, "Soft ban issued by ${event.user.asTag} (${event.user.id})")
            .delay(1, TimeUnit.SECONDS)
            .flatMap { event.guild.unban(user) }
            .flatMap { event.hook.editOriginal('✅ User has been successfully soft banned!') }
            .queue()
    }
}
