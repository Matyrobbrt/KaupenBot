package com.matyrobbrt.kaupenbot.extensions.logging

import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.extensions.logging.ModLogs.ActionData
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.audit.AuditLogKey
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.api.utils.TimeFormat

import java.awt.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.function.Consumer
import java.util.function.UnaryOperator

@CompileStatic
@RegisterExtension(value = 'moderationLogs', botId = 'kbot')
final class ModerationLogsExtension implements BotExtension {
    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(GuildMemberUpdateTimeOutEvent) { GuildMemberUpdateTimeOutEvent event ->
            if (event.oldTimeOutEnd === null && event.newTimeOutEnd !== null) {
                // Somebody was timed out
                final ActionData data = ModLogs.getData(ActionType.MEMBER_UPDATE, event.user.idLong)
                if (data === null) {
                    getAuditLog(event.guild, event.user.idLong, { it.type(ActionType.MEMBER_UPDATE).limit(5) }) { log ->
                        if (log.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT) !== null) {
                            handleTimeout(event, log.user.idLong, log.reason)
                        }
                    }
                } else {
                    handleTimeout(event, data.moderator(), data.reason())
                }
            } else if (event.oldTimeOutEnd !== null && event.newTimeOutEnd === null) {
                // Somebody was un-timed out
                final ActionData data = ModLogs.getData(ActionType.MEMBER_UPDATE, event.user.idLong)
                if (data === null) {
                    getAuditLog(event.guild, event.user.idLong, { it.type(ActionType.MEMBER_UPDATE).limit(5) }) { log ->
                        if (log.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT) !== null) {
                            handleTimeoutRemove(event, log.user.idLong)
                        }
                    }
                } else {
                    handleTimeoutRemove(event, data.moderator())
                }
            }
        }

        jda.subscribe(GuildMemberRemoveEvent) { GuildMemberRemoveEvent event ->
            final ActionData data = ModLogs.getData(ActionType.KICK, event.user.idLong)
            if (data === null) {
                getAuditLog(event.guild, event.user.idLong, { it.type(ActionType.KICK).limit(5) }) { log ->
                    if (log.timeCreated.toInstant().isAfter(Instant.now().minus(2, ChronoUnit.MINUTES))) {
                        handleKick(event, log.user.idLong, log.reason)
                    }
                }
            } else {
                handleKick(event, data.moderator(), data.reason())
            }
        }

        jda.subscribe(GuildBanEvent) { GuildBanEvent event ->
            final ActionData data = ModLogs.getData(ActionType.BAN, event.user.idLong)
            if (data === null) {
                getAuditLog(event.guild, event.user.idLong, { it.type(ActionType.BAN).limit(5) }) { log ->
                    if (log.timeCreated.toInstant().isAfter(Instant.now().minus(2, ChronoUnit.MINUTES))) {
                        handleBan(event, log.user.idLong, log.reason)
                    }
                }
            } else {
                handleBan(event, data.moderator(), data.reason())
            }
        }
    }

    private static void handleKick(final GuildMemberRemoveEvent event, long moderator, String reason) {
        log(event.guild, embed {
            sentNow()
            color = RUBY
            title = 'User Kicked'
            thumbnail = event.user.effectiveAvatarUrl
            addField('**Kick Reason:**', reason ?: 'Reason for kick was not provided or could not be found.', false)
            description = "${event.user.asMention} / ${event.user.asTag} (${event.user.id}) was kicked by <@$moderator> ($moderator)."
        })
    }

    private static void handleBan(final GuildBanEvent event, long moderator, String reason) {
        log(event.guild, embed {
            sentNow()
            color = Color.RED
            title = 'User Banned'
            thumbnail = event.user.effectiveAvatarUrl
            addField('**Ban Reason:**', reason ?: 'Reason for ban was not provided or could not be found.', false)
            description = "${event.user.asMention} / ${event.user.asTag} (${event.user.id}) was banned by <@$moderator> ($moderator)."
        })
    }

    public static final Color RUBY = new Color(0xE91E63)
    public static final Color LIGHT_SEA_GREEN = new Color(0x1ABC9C)

    private static void handleTimeout(final GuildMemberUpdateTimeOutEvent event, long moderator, String reason) {
        log(event.guild, embed {
            sentNow()
            color = LIGHT_SEA_GREEN
            title = 'User timed out'
            thumbnail = event.user.effectiveAvatarUrl
            addField('**User**:', "${event.user.asMention} / ${event.user.asTag} (${event.user.idLong})", false)
            addField('**Reason**:', reason ?: 'Reason was not provided or could not be found.', false)
            addField('**Time out end**:', TimeFormat.RELATIVE.format(event.newTimeOutEnd), false)
            addField('**Moderator**:', "<@$moderator> ($moderator)", false)
        })
    }
    private static void handleTimeoutRemove(final GuildMemberUpdateTimeOutEvent event, long moderator) {
        log(event.guild, embed {
            sentNow()
            color = RUBY
            title = 'User timeout removed'
            thumbnail = event.user.effectiveAvatarUrl
            addField('**User**:', "${event.user.asMention} / ${event.user.asTag} (${event.user.idLong})", false)
            addField('**Moderator**:', "<@$moderator> ($moderator)", false)
        })
    }

    static void log(final Guild guild, MessageEmbed embed) {
        if (KaupenBot.config.loggingChannels.moderationLogs != 0) {
            guild.getJDA().getChannelById(MessageChannel, KaupenBot.config.loggingChannels.moderationLogs)
                .sendMessageEmbeds(embed)
                .queue()
        }
    }

    static void getAuditLog(final Guild guild, final long targetId, UnaryOperator<AuditLogPaginationAction> modifier, Consumer<AuditLogEntry> consumer, Runnable orElse = () -> {}) {
        modifier.apply(guild.retrieveAuditLogs())
                .queue(logs -> logs.stream()
                        .filter(entry -> entry.getTargetIdLong() == targetId)
                        .findFirst()
                        .ifPresentOrElse(consumer, orElse))
    }
}
