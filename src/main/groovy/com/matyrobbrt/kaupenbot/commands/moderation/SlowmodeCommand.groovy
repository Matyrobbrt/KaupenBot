package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.api.ModerationActionType
import com.matyrobbrt.kaupenbot.common.util.TimeUtils
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

@CompileStatic
final class SlowmodeCommand extends SlashCommand {
    private final SetCommand set = new SetCommand()
    SlowmodeCommand() {
        name = 'slowmode'
        help = 'Set slowmode for a channel'
        userPermissions = new Permission[] { Permission.MODERATE_MEMBERS }
        children = new SlashCommand[] {
            set, new Disable()
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    @Override
    protected void execute(CommandEvent event) {
        set.execute(event)
    }

    @CompileStatic
    private static final class SetCommand extends SlashCommand {
        SetCommand() {
            name = 'set'
            help = 'Set slowmode in a channel'
            options = [
                    new OptionData(OptionType.STRING, 'rate', 'The slowmode rate to set', true),
                    new OptionData(OptionType.CHANNEL, 'channel', 'The channel to change the slowmode in')
            ]
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final channel = event.textChannel('channel') ?: event.channel.asTextChannel()
            final rate = TimeUtils.getDurationFromInput(event.string('rate'))
            if (rate.toSeconds() > TextChannel.MAX_SLOWMODE) {
                event.replyProhibited('Cannot set slowmode to values greater than 6 hours!').queue()
                return
            }

            final durationText = ModerationActionType.formatDuration(rate)
            channel.manager.setSlowmode(rate.toSeconds() as int)
                .reason("Slowmode issued by ${event.user.asTag} (${event.user.id})")
                .flatMap { event.reply("Set slowmode to `$durationText`.") }
                .flatMap({ event.channel != channel }) { channel.sendMessage("Slowmode has been set to `$durationText` by moderators.") }
                .queue()
        }

        @Override
        void execute(CommandEvent event) {
            final rate = TimeUtils.getDurationFromInput(event.args)
            if (rate.toSeconds() > TextChannel.MAX_SLOWMODE) {
                event.message.reply('Cannot set slowmode to values greater than 6 hours!').queue()
                return
            }

            final durationText = ModerationActionType.formatDuration(rate)
            event.textChannel.manager.setSlowmode(rate.toSeconds() as int)
                    .reason("Slowmode issued by ${event.author.asTag} (${event.author.id})")
                    .flatMap { event.message.reply("Set slowmode to `$durationText`.") }
                    .queue()
        }
    }

    @CompileStatic
    private static final class Disable extends SlashCommand {

        Disable() {
            name = 'disable'
            help = 'Disable slowmode in a channel'
            options = [
                    new OptionData(OptionType.CHANNEL, 'channel', 'The channel to disable the slowmode in')
            ]
            aliases = new String[] { 'off' }
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final channel = event.textChannel('channel') ?: event.channel.asTextChannel()

            if (channel.slowmode <= 0) {
                event.replyProhibited('Channel does not have slowmode enabled!').queue()
                return
            }

            channel.manager.setSlowmode(0)
                    .reason("Slowmode disabled by ${event.user.asTag} (${event.user.id})")
                    .flatMap { event.reply("Disabled slowmode!") }
                    .flatMap({ event.channel != channel }) { channel.sendMessage("Slowmode has been disabled by moderators.") }
                    .queue()
        }

        @Override
        protected void execute(CommandEvent event) {
            final channel = event.textChannel
            if (channel.slowmode <= 0) {
                event.message.reply('Channel does not have slowmode enabled!').queue()
                return
            }

            channel.manager.setSlowmode(0)
                    .reason("Slowmode disabled by ${event.author.asTag} (${event.author.id})")
                    .flatMap { event.message.reply("Disabled slowmode!") }
                    .queue()
        }
    }
}
