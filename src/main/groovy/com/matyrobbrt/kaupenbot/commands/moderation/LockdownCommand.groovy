package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.db.LockdownsDAO
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction

import java.util.stream.Collectors

@CompileStatic
final class LockdownCommand extends SlashCommand {

    LockdownCommand() {
        name = 'lockdown'
        help = 'Lockdowns a channel'
        userPermissions = new Permission[] { Permission.MODERATE_MEMBERS }
        options.add(new OptionData(OptionType.CHANNEL, 'channel', 'The channel to lockdown'))
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final channel = event.messageChannel('channel') ?: event.channel.asGuildMessageChannel()
        channel.sendMessage('Channel has been locked by moderators.')
            .flatMap { lockdownChannel(channel, "Lockdown issued by ${event.user.asTag}: ${event.user.id}")}
            .flatMap { event.replyEphemeral('Channel locked down!') }
            .queue()
    }

    @Override
    protected void execute(CommandEvent event) {
        final channel = (event.message.mentions.channels.find() ?: event.guildChannel) as GuildMessageChannel
        channel.sendMessage('Channel has been locked by moderators.')
                .flatMap { lockdownChannel(channel, "Lockdown issued by ${event.author.asTag}: ${event.author.id}")}
                .flatMap({ channel.idLong !== event.channel.idLong }) { event.message.reply('Channel locked down!') }
                .queue()
    }

    static RestAction lockdownChannel(GuildChannel channel, String reason) {
        final toModify = channel.permissionContainer.permissionOverrides
            // Only modify overrides for roles / members which can view the channel
            .findAll { Permission.VIEW_CHANNEL in it.allowed && Permission.MANAGE_CHANNEL !in it.allowed }

        if (toModify.empty) {
            return channel.permissionContainer.manager.putRolePermissionOverride(
                    channel.guild.publicRole.idLong, null, [Permission.MESSAGE_SEND]
            ).reason(reason)
        }

        final Map<PermissionOwner, PermissionData> data = toModify.stream()
            .collect(Collectors.<PermissionOverride, PermissionOwner, PermissionData>toMap(
                    { new PermissionOwner(it.idLong, it.isMemberOverride() ? PermissionOwner.PermissionType.MEMBER : PermissionOwner.PermissionType.ROLE)},
                    { new PermissionData(it.allowedRaw, it.deniedRaw) }
            ))
        KaupenBot.database.useExtension(LockdownsDAO) { LockdownsDAO db ->
            db.insert(channel.idLong, data)
        }
        return channel.permissionContainer.manager
                .putMemberPermissionOverride(channel.guild.selfMember.idLong, [Permission.MANAGE_CHANNEL], null)
                .putRolePermissionOverride(KaupenBot.config.moderatorRole, [Permission.MANAGE_CHANNEL], null)
                .flatMap {
                    RestAction.allOf(toModify.stream()
                            .map { it.manager.deny(Permission.MESSAGE_SEND).reason(reason) }
                            .toList())
                }
    }
}

@CompileStatic
final class UnlockdownCommand extends SlashCommand {

    UnlockdownCommand() {
        name = 'unlockdown'
        help = 'Unlockdowns a channel'
        userPermissions = new Permission[] { Permission.MODERATE_MEMBERS }
        options.add(new OptionData(OptionType.CHANNEL, 'channel', 'The channel to un-lockdown'))
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final channel = event.messageChannel('channel') ?: event.channel.asGuildMessageChannel()
        unlockdownChannel(channel, "Un-lockdown issued by ${event.user.asTag}: ${event.user.id}")
            .flatMap { channel.sendMessage('Channel has been unlocked by moderators.') }
            .flatMap { event.replyEphemeral('Channel unlocked!') }
            .queue()
    }

    @Override
    protected void execute(CommandEvent event) {
        final channel = (event.message.mentions.channels.find() ?: event.guildChannel) as GuildMessageChannel
        unlockdownChannel(channel, "Lockdown issued by ${event.author.asTag}: ${event.author.id}")
            .flatMap { channel.sendMessage('Channel has been unlocked by moderators!') }
            .flatMap({ channel.idLong !== event.channel.idLong }) { event.message.reply('Channel unlocked!') }
            .queue()
    }

    static RestAction unlockdownChannel(GuildChannel channel, String reason) {
        final old = KaupenBot.database.withExtension(LockdownsDAO) { it.get(channel.idLong) }

        if (old === null) {
            return channel.permissionContainer.manager.putRolePermissionOverride(
                    channel.guild.publicRole.idLong, [Permission.MESSAGE_SEND], null
            ).reason(reason)
        }
        KaupenBot.database.useExtension(LockdownsDAO) { it.delete(channel.idLong) }

        return RestAction.allOf(old.entrySet()
            .stream().map {
                final manager = channel.permissionContainer.manager
                it.key.type === PermissionOwner.PermissionType.ROLE ? manager.putRolePermissionOverride(it.key.owner, it.value.allowed, it.value.denied)
                        : manager.putMemberPermissionOverride(it.key.owner, it.value.allowed, it.value.denied)
            }
            .toList())
    }
}

@CompileStatic
final class PermissionOwner {
    public final long owner
    public final PermissionType type

    PermissionOwner(long owner, PermissionType type) {
        this.owner = owner
        this.type = type
    }

    long getOwner() {
        return owner
    }

    PermissionType getType() {
        return type
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() !== o.class) return false

        PermissionOwner that = (PermissionOwner) o

        if (owner !== that.owner) return false
        if (type !== that.type) return false

        return true
    }

    int hashCode() {
        int result
        result = (int) (owner ^ (owner >>> 32))
        result = 31 * result + (type !== null ? type.hashCode() : 0)
        return result
    }

    static enum PermissionType { ROLE, MEMBER }
}

@CompileStatic
final class PermissionData {
    public final long allowed, denied

    PermissionData(long allowed, long denied) {
        this.allowed = allowed
        this.denied = denied
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() !== o.class) return false

        PermissionData that = (PermissionData) o

        if (allowed !== that.allowed) return false
        if (denied !== that.denied) return false

        return true
    }

    int hashCode() {
        int result
        result = (int) (allowed ^ (allowed >>> 32))
        result = 31 * result + (int) (denied ^ (denied >>> 32))
        return result
    }
}