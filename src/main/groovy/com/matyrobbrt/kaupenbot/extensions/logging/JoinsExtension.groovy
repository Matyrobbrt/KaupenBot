package com.matyrobbrt.kaupenbot.extensions.logging

import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.utils.TimeFormat

import java.awt.Color

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'joins')
final class JoinsExtension implements BotExtension {
    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(GuildMemberJoinEvent) {
            final role = it.guild.getRoleById(KaupenBot.config.joinRole)
            if (role !== null) guild.addRoleToMember(member, role).reason('Join Role').queue()

            final log = getLogChannel(jda)
            if (log !== null) {
                final embed = new EmbedBuilder()
                        .sentNow()
                        .setColor(Color.GREEN)
                        .setTitle("User Joined")
                        .setFooter("User ID: " + member.getId())
                        .addField("User:", "$user.asTag ($user.asMention)", true)
                        .setThumbnail(member.getEffectiveAvatarUrl())
                        .addField("Joined Discord:", TimeFormat.DATE_TIME_SHORT.format(user.getTimeCreated()), true)
                        .build()
                log.sendMessageEmbeds(embed).queue()
            }
        }
        jda.subscribe(GuildMemberRemoveEvent) {
            if (it.member === null) return
            final log = getLogChannel(jda)
            if (log !== null) {
                final embed = new EmbedBuilder()
                        .sentNow()
                        .setColor(Color.RED)
                        .setTitle('User Left')
                        .setFooter('User ID: ' + member.getId(), member.getEffectiveAvatarUrl())
                        .addField('User:', user.getAsTag(), true)
                        .addField('Join Time:', TimeFormat.DATE_TIME_SHORT.format(member.getUser().getTimeCreated()), true)
                        .addField('Roles', IMentionable.orNone(member.getRoles()), false)
                        .setThumbnail(user.getAvatarUrl())
                        .build()
                log.sendMessageEmbeds(embed).queue()
            }
        }
    }

    static MessageChannel getLogChannel(JDA jda) {
        return jda.getChannelById(MessageChannel, KaupenBot.config.loggingChannels.leaveJoinLogs)
    }
}
