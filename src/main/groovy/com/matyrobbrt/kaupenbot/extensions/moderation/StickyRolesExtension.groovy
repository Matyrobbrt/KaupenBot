package com.matyrobbrt.kaupenbot.extensions.moderation

import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.db.StickyRolesDAO
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent

import java.util.stream.Collectors

@CompileStatic
@RegisterExtension(value = 'stickyRoles', botId = 'kbot')
final class StickyRolesExtension implements BotExtension {
    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(GuildMemberJoinEvent) { GuildMemberJoinEvent event ->
            KaupenBot.database.useExtension(StickyRolesDAO, db -> {
                final roles = db.getRoles(event.getUser().getIdLong(), event.getGuild().getIdLong()).stream()
                        .map { event.guild.getRoleById(it) }
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection { new ArrayList<Role>() })

                final role = event.guild.getRoleById(KaupenBot.config.joinRole)
                if (role !== null) roles.add(role)

                if (!roles.isEmpty()) {
                    event.getGuild()
                            .modifyMemberRoles(event.getMember(), roles)
                            .reason('Persisted roles')
                            .queue()
                }
                db.clear(event.getUser().getIdLong(), event.getGuild().getIdLong())
            })
        }

        jda.subscribe(GuildMemberRemoveEvent) { GuildMemberRemoveEvent event ->
            if (event.user === null || event.member === null) return

            KaupenBot.database.useExtension(StickyRolesDAO, db -> {
                final long joinRole = KaupenBot.config.joinRole
                db.clear(event.getUser().getIdLong(), event.getGuild().getIdLong())
                final var roles = event.getMember().getRoles()
                        .stream()
                        .filter(r -> !r.isManaged() && event.getGuild().getSelfMember().canInteract(r) && joinRole != r.idLong)
                        .map(ISnowflake.&getIdLong)
                        .toList()
                if (!roles.isEmpty()) {
                    db.insert(event.getUser().getIdLong(), event.getGuild().getIdLong(), roles)
                }
            })
        }
    }
}
