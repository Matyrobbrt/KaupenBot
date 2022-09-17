package com.matyrobbrt.kaupenbot.commands.extensions

import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.commands.api.BotExtension
import com.matyrobbrt.kaupenbot.commands.api.RegisterExtension
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent

@CompileStatic
@RegisterExtension
final class JoinsExtension implements BotExtension {
    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(GuildMemberJoinEvent) {
            final role = KaupenBot.jda.getRoleById(KaupenBot.config.joinRole)
            if (role !== null) guild.addRoleToMember(member, role).reason('Join Role').queue()
        }
    }
}
