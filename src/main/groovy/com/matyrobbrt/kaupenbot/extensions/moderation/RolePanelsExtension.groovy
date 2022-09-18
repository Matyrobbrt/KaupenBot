//file:noinspection GrMethodMayBeStatic
package com.matyrobbrt.kaupenbot.extensions.moderation

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectMenuInteraction
import net.dv8tion.jda.api.interactions.components.selections.SelectOption

import java.awt.Color

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'role-panels')
final class RolePanelsExtension implements BotExtension {

    private final Map<Long, SelfRoleData> data = Collections.synchronizedMap([:])

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            guildOnly = true
            name = 'role-panel'
            require Permission.MANAGE_ROLES
            description = 'Commands to manage role panels.'
            subCommand {
                name = 'stop'
                description = 'Stop a role panel configuration, and send the message.'
                options = [
                        new OptionData(OptionType.STRING, 'id', 'The message ID of the ongoing configuration.', true),
                        new OptionData(OptionType.STRING, 'type', 'The type of the role panel to create.', true).addEnum(PanelType),
                        new OptionData(OptionType.BOOLEAN, 'drop', 'If the self role configuration should be dropped. Default: false')
                ]
                checkIf({ data.containsKey(it.getOption('id')?.asLong) }, 'Unknown configuration message ID.')
                action {
                    final id = getOption('id')?.asLong
                    if (getOption('drop')?.asBoolean) {
                        data.remove(id)
                        replyEphemeral("Dropped role panel with ID $id!").queue()
                        return
                    }
                    deferReply().queue()
                    final data = this.data[id]
                    final embed = embed {
                        title = data.title
                        color = data.colour
                        description = data.description + '\n\n'
                        final Iterator<Map.Entry<Emoji, EmojiData>> iterator = data.emojis.iterator()
                        while (iterator.hasNext()) {
                            final Map.Entry<Emoji, EmojiData> next = iterator.next()
                            appendDescription("${next.key.formatted}: ${next.value.description()}")
                            if (iterator.hasNext()) appendDescription('\n')
                        }
                    }
                    final type = enumOption(PanelType, 'type')
                    final List<? extends ActionComponent> components = switch (type) {
                        case PanelType.SELECTION_MENU -> List.of(SelectMenu.create('role-panel').tap {
                            for (final entry : data.emojis) {
                                final role = KaupenBot.jda.getRoleById(entry.value.roleId())
                                if (role !== null) {
                                    it.addOption(role.name, String.valueOf(entry.value.roleId()), entry.key)
                                }
                            }
                        }.setMaxValues(data.emojis.size()).setMinValues(0).build())
                        case PanelType.BUTTONS -> data.emojis.entrySet().stream()
                            .map {
                                final role = KaupenBot.jda.getRoleById(it.value.roleId())
                                if (role !== null) {
                                    return Button.secondary("role-panel/${role.id}", it.key)
                                }
                                return null
                            }.filter { it !== null }.toList()
                    }
                    final List<ActionRow> rows = []
                    List<ItemComponent> current = []
                    components.each {
                        if (current.size() >= it.maxPerRow) {
                            rows.add(ActionRow.of(current))
                            current = []
                            current.add(it)
                        } else {
                            current.add(it)
                        }
                    }
                    if (!current.isEmpty())
                        rows.add(ActionRow.of(current))
                    guild.getChannelById(MessageChannel, data.targetChannel).sendMessageEmbeds(embed)
                        .addComponents(rows)
                        .flatMap { hook.sendMessage("Successfully sent role panel! [Jump to message]($it.jumpUrl).").setSuppressEmbeds(true) }
                        .queue((_) -> this.data.remove(id))
                }
            }
            subCommand {
                name = 'start'
                description = 'Start a role panel configuration.'
                options = [
                        new OptionData(OptionType.CHANNEL, 'channel', 'The channel to send the role panel in.'),
                        new OptionData(OptionType.STRING, 'title', 'Role panel title'),
                        new OptionData(OptionType.STRING, 'description', 'Role panel description'),
                        new OptionData(OptionType.STRING, 'colour', 'Role panel colour')
                ]
                action {
                    final target = getOption('channel')?.asChannel?.asGuildMessageChannel()
                    reply('Started role panel configuration! Please react with the emoji you want to assign a role to!')
                        .flatMap { it.retrieveOriginal() }
                        .queue {
                            it.editMessage(it.contentRaw + ' Message ID: ' + it.id).queue()
                            data.put(it.idLong, new SelfRoleData(target.idLong, string('title'), string('description'), Color.decode(string('colour', '0x000000'))))
                        }
                }
            }
        }
    }

    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(MessageReactionAddEvent) {
            final messageData = data[it.messageIdLong]
            if (messageData === null) return
            messageData.currentEmoji = it.reaction.emoji
            it.retrieveMessage().flatMap {
                it.editMessage(it.contentRaw).setEmbeds(embed {
                    description = "Currently configuring: ${messageData.currentEmoji.formatted}"
                })
            }.flatMap { _ -> it.reaction.removeReaction(it.user) }
            .queue()
        }
        jda.subscribe(MessageReceivedEvent) {
            if (it.message.messageReference === null) return
            final messageData = data[it.message.messageReference.messageIdLong]
            if (messageData === null || messageData.currentEmoji === null || !it.member.hasPermission(Permission.MANAGE_ROLES)) return
            final contentSplit = it.message.contentRaw.split(':')
            try {
                final roleId = Long.parseLong(contentSplit[0].trim())
                final emoji = messageData.currentEmoji
                final desc = contentSplit.drop(1).join(':').trim()
                messageData.emojis[emoji] = new EmojiData(roleId, desc)
                messageData.currentEmoji = null
                it.message.delete().flatMap { _ ->
                    it.message.channel.retrieveMessageById(it.message.messageReference.messageIdLong)
                }.flatMap {
                    it.editMessage(it.contentRaw + "\n${emoji.formatted}: <@&${roleId}>: $desc").setEmbeds()
                }.queue()
            } catch (NumberFormatException ignored) {}
        }

        jda.subscribe(SelectMenuInteractionEvent) {
            if (!fromGuild || selectMenu.id != 'role-panel') return
            final var selfMember = guild.getSelfMember()
            final var selectedRoles = selectedOptions.stream()
                    .map(SelectOption::getValue)
                    .map { guild.getRoleById(it) }
                    .filter(Objects::nonNull)
                    .filter { selfMember.canInteract(it) }
                    .toList()

            handleRoleSelection(interaction, selectedRoles, guild)
        }

        jda.subscribe(ButtonInteractionEvent) {
            if (!fromGuild || it.button.id === null) return
            final splitId = it.button.id.split('/')
            if (splitId[0] != 'role-panel' || splitId.length !== 2) return
            final role = guild.getRoleById(splitId[1])
            if (role === null) {
                replyProhibited('Unknown role!').queue()
            } else {
                final boolean hadRole = role in member.roles
                (hadRole ? guild.removeRoleFromMember(user, role) : guild.addRoleToMember(user, role))
                    .reason('Role Selection').flatMap {
                        replyEphemeral("Successfully ${hadRole ? 'added' : 'removed'} role!")
                    }.queue()
            }
        }
    }

    private void handleRoleSelection(final SelectMenuInteraction interaction, final Collection<Role> selectedRoles, final Guild guild) {
        final var member = Objects.requireNonNull(interaction.getMember())
        final var toAdd = new ArrayList<Role>(selectedRoles.size())
        final var toRemove = new ArrayList<Role>(selectedRoles.size())

        interaction
                .getComponent()
                .getOptions()
                .stream()
                .map(selectOption -> {
                    final var role = guild.getRoleById(selectOption.getValue())

                    if (role === null) {
                        KaupenBot.log.warn(
                                "The {} ({}) role doesn't exist anymore but it is still an option in a selection menu!",
                                selectOption.getLabel(), selectOption.getValue())
                    }

                    return role
                })
                .filter(Objects::nonNull)
                .forEach(role -> {
                    if (selectedRoles.contains(role)) {
                        toAdd.add(role)
                    } else {
                        toRemove.add(role)
                    }
                })

        guild.modifyMemberRoles(member, toAdd, toRemove)
                .reason('Role Selection')
                .flatMap { interaction.replyEphemeral('Successfully updated your roles!') }
                .queue()
    }

    @CompileStatic
    @TupleConstructor(excludes = ['emojis', 'currentEmoji'])
    static final class SelfRoleData {
        final Map<Emoji, EmojiData> emojis = [:]
        final long targetChannel
        final String title
        final String description
        final Color colour

        Emoji currentEmoji
    }

    @CompileStatic
    static record EmojiData(long roleId, String description) {}

    static enum PanelType {
        SELECTION_MENU {
            @Override
            String toString() {
                'Selection menu'
            }
        },
        BUTTONS {
            @Override
            String toString() {
                'Buttons'
            }
        }
    }
}
