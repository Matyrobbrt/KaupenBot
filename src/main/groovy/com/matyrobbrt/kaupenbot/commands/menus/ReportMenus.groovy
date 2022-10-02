package com.matyrobbrt.kaupenbot.commands.menus

import com.jagrosh.jdautilities.command.CooldownScope
import com.jagrosh.jdautilities.command.MessageContextMenu
import com.jagrosh.jdautilities.command.MessageContextMenuEvent
import com.jagrosh.jdautilities.command.UserContextMenu
import com.jagrosh.jdautilities.command.UserContextMenuEvent
import com.matyrobbrt.kaupenbot.KaupenBot
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle

import java.awt.Color

@CompileStatic
final class ReportUser extends UserContextMenu {
    ReportUser() {
        name = 'Report User'
        cooldownScope = CooldownScope.USER
        cooldown = 10
    }

    @Override
    protected void execute(UserContextMenuEvent event) {
        final reportChannel = event.getJDA().getChannelById(MessageChannel, KaupenBot.config.channels.reportChannel)
        if (reportChannel === null) event.replyProhibited('Report channel is not configured!')

        event.replyAndWaitModal(Modal.create('__', 'Report User')
            .addActionRow(TextInput.create('reason', 'Reason', TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setPlaceholder('Reason for report')
                .setMaxLength(MessageEmbed.VALUE_MAX_LENGTH)
                .build()), {
            final reason = it.getValue('reason')?.asString ?: '*No reason was provided.*'
            reportChannel.sendMessageEmbeds(embed {
                sentNow()
                color = Color.RED
                setTitle('User reported')
                setDescription("${event.target.asMention} (${event.target.id}) was reported by ${event.user.asMention} (${event.user.id}).")
                setFooter(event.user.asTag, event.user.effectiveAvatarUrl)
                addField('Reason', reason, false)
            }).flatMap { _ -> it.replyEphemeral('Successfully reported user!') }
            .queue()
        }).queue()
    }
}

@CompileStatic
final class ReportMessage extends MessageContextMenu {
   ReportMessage() {
        name = 'Report Message'
        cooldownScope = CooldownScope.USER
        cooldown = 10
    }

    @Override
    protected void execute(MessageContextMenuEvent event) {
        final reportChannel = event.getJDA().getChannelById(MessageChannel, KaupenBot.config.channels.reportChannel)
        if (reportChannel === null) event.replyProhibited('Report channel is not configured!')

        event.replyAndWaitModal(Modal.create('__', 'Report Message')
            .addActionRow(TextInput.create('reason', 'Reason', TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setPlaceholder('Reason for report')
                .setMaxLength(MessageEmbed.VALUE_MAX_LENGTH)
                .build()), {
            final reason = it.getValue('reason')?.asString ?: '*No reason was provided.*'
            reportChannel.sendMessageEmbeds(embed {
                sentNow()
                color = Color.RED
                setTitle('Message reported')
                setDescription("The message sent by ${event.target.author.asMention} (${event.target.author.id}) in ${event.target.channel.asMention} was reported by ${event.user.asMention} (${event.user.id}).")
                setFooter(event.user.asTag, event.user.effectiveAvatarUrl)
                addField('Reason', reason, false)
            })
            .setContent(event.target.contentRaw)
            .flatMap { _ -> it.replyEphemeral('Successfully reported message!') }
            .queue()
        }).queue()
    }
}
