//file:noinspection GrMethodMayBeStatic
package com.matyrobbrt.kaupenbot.extensions.moderation

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.db.RolePanelsDAO
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder

import java.awt.Color
import java.util.stream.Collectors

@CompileStatic
@RegisterExtension(value = 'rolePanels', botId = 'kbot')
final class RolePanelsExtension implements BotExtension {
    private static final String ID = 'role-panel'

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'role-panels'
            require(Permission.MANAGE_ROLES)
            subCommand {
                name = 'create'
                options = [
                        new OptionData(OptionType.STRING, 'id', 'Panel ID', true),
                        new OptionData(OptionType.STRING, 'mode', 'The panel mode', true).addEnum(PanelMode),
                        new OptionData(OptionType.STRING, 'type', 'The panel type', true).addEnum(PanelType),
                        new OptionData(OptionType.STRING, 'title', 'Panel title'),
                        new OptionData(OptionType.STRING, 'description', 'Panel description'),
                        new OptionData(OptionType.STRING, 'colour', 'Panel colour')
                ]
                description = 'Create a new role panel'

                action {
                    final id = it.string('id')
                    if (KaupenBot.database.withExtension(RolePanelsDAO) { it.get(id) !== null }) {
                        replyProhibited('A role panel with that ID exists already!').queue()
                        return
                    }

                    final embed = new EmbedBuilder()
                    embed.title = it.string('title') ?: id
                    if (it.getOption('description')) embed.description = it.string('description')
                    if (it.getOption('colour')) embed.color = Color.decode(it.string('colour'))

                    embed.footer = "Role Panel ID: $id"
                    it.channel.sendMessageEmbeds(embed.build())
                            .flatMap { _ ->
                                KaupenBot.database.useExtension(RolePanelsDAO) { RolePanelsDAO db ->
                                    db.insert(id, it.channel.idLong, _.idLong, it.enumOption(PanelMode, 'mode'), it.enumOption(PanelType, 'type'))
                                }
                                it.replyEphemeral('Role panel created!')
                            }
                            .queue()
                }
            }
            subCommand {
                name = 'delete'
                description = 'Delete a role panel'
                options = [
                        new OptionData(OptionType.STRING, 'id', 'The role panel ID', true).setAutoComplete(true)
                ]

                action {
                    final id = it.string('id')
                    final info = KaupenBot.database.withExtension(RolePanelsDAO) { it.get(id) }
                    if (info === null) {
                        replyProhibited('Unknown role panel!').queue()
                        return
                    }
                    KaupenBot.database.useExtension(RolePanelsDAO) { it.remove(id) }
                    it.getJDA().getChannelById(MessageChannel, info.getChannelId())
                        .deleteMessageById(info.messageId)
                        .flatMap { _ -> it.replyEphemeral('Successfully deleted role panel!') }
                        .queue()
                }

                autoCompleteOption('id') { current ->
                    replyChoiceStrings(KaupenBot.database.withExtension(RolePanelsDAO) { it.allIds }.findAll {
                        it.startsWith(current)
                    }).queue()
                }
            }

            group {
                name = 'role'
                subCommand {
                    name = 'add'
                    description = 'Add a role to a panel'
                    options = [
                            new OptionData(OptionType.STRING, 'id', 'The panel ID', true).setAutoComplete(true),
                            new OptionData(OptionType.ROLE, 'role', 'The role to add to the panel', true),
                            new OptionData(OptionType.STRING, 'emoji', 'The emoji to represent the role'),
                            new OptionData(OptionType.STRING, 'description', "The role's description")
                    ]

                    action {
                        final info = KaupenBot.database.withExtension(RolePanelsDAO) { db -> db.get(it.string('id')) }
                        if (info === null) {
                            replyProhibited('Unknown role panel!').queue()
                            return
                        }
                        final role = it.role('role')
                        it.deferReply(true).queue()
                        final emoji = it.string('emoji')?.parseEmojis()?.find()
                        it.getJDA().getChannelById(MessageChannel, info.getChannelId())
                            .retrieveMessageById(info.getMessageId())
                            .flatMap { msg ->
                                return switch (info.mode) {
                                    case PanelMode.SelectMenu -> {
                                        final SelectMenu.Builder menu = msg.actionRows.empty ? StringSelectMenu.create(ID) : (msg.actionRows[0].iterator().find() as StringSelectMenu).createCopy()
                                        menu.addOption(role.name, role.id, it.string('description'), emoji)
                                        menu.setMinValues(0).setMaxValues(menu.options.size())
                                        yield msg.editMessage(new MessageEditBuilder().build())
                                            .setActionRow(menu.build())
                                    }
                                    case PanelMode.Buttons -> {
                                        final components = msg.actionRows.stream()
                                                .flatMap { it.toList().stream() }
                                                .collect(Collectors.toCollection { new ArrayList<ItemComponent>() })
                                        components.add(Button.secondary(ID + '/' + role.id, (emoji?.type == Emoji.Type.UNICODE ? (emoji.formatted + ' ') : '') + role.name))
                                        yield msg.editMessage(new MessageEditBuilder().build())
                                            .setComponents(ActionRow.partitionOf(components))
                                    }
                                }
                            }
                            .flatMap { _ -> it.hook.editOriginal('Successfully added role!') }
                            .queue()
                    }

                    autoCompleteOption('id') { current ->
                        replyChoiceStrings(KaupenBot.database.withExtension(RolePanelsDAO) { it.allIds }.findAll {
                            it.startsWith(current)
                        }).queue()
                    }
                }

                subCommand {
                    name = 'remove'
                    description = 'Remove a role from a role panel'
                    options = [
                            new OptionData(OptionType.STRING, 'id', 'Role panel ID', true).setAutoComplete(true),
                            new OptionData(OptionType.ROLE, 'role', 'The role to remove', true)
                    ]
                    action {
                        final info = KaupenBot.database.withExtension(RolePanelsDAO) { db -> db.get(it.string('id')) }
                        if (info === null) {
                            replyProhibited('Unknown role panel!').queue()
                            return
                        }

                        final role = it.role('role')
                        it.deferReply(true).queue()
                        it.getJDA().getChannelById(MessageChannel, info.getChannelId())
                                .retrieveMessageById(info.getMessageId())
                                .flatMap { msg ->
                                    return switch (info.mode) {
                                        case PanelMode.SelectMenu -> {
                                            final StringSelectMenu.Builder menu = msg.actionRows.empty ? StringSelectMenu.create(ID) : (msg.actionRows[0].iterator().find() as StringSelectMenu).createCopy()
                                            menu.options.removeIf { it.value == role.id }
                                            yield msg.editMessage(new MessageEditBuilder().build())
                                                    .setActionRow(menu.build())
                                        }
                                        case PanelMode.Buttons -> {
                                            final components = msg.actionRows.stream()
                                                    .flatMap { it.toList().stream() }
                                                    .collect(Collectors.toCollection { new ArrayList<ItemComponent>() })
                                            components.removeIf { it instanceof Button && it.id.contains(role.id) }
                                            yield msg.editMessage(new MessageEditBuilder().build())
                                                    .setComponents(ActionRow.partitionOf(components))
                                        }
                                    }
                                }
                                .flatMap { _ -> it.hook.editOriginal('Successfully removed role!') }
                                .queue()
                    }

                    autoCompleteOption('id') { current ->
                        replyChoiceStrings(KaupenBot.database.withExtension(RolePanelsDAO) { it.allIds }.findAll {
                            it.startsWith(current)
                        }).queue()
                    }
                }
            }
        }
    }

    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(StringSelectInteractionEvent) {
            if (!fromGuild || it.getSelectMenu().id != ID) return
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
            if (splitId[0] != ID || splitId.length !== 2) return
            final role = guild.getRoleById(splitId[1])
            if (role !== null) {
                final boolean hadRole = role in member.roles
                (hadRole ? guild.removeRoleFromMember(user, role) : guild.addRoleToMember(user, role))
                        .reason('Role Selection').flatMap {
                    replyEphemeral("Successfully ${hadRole ? 'removed' : 'added'} role!")
                }.queue()
            } else {
                replyProhibited('Unknown role!').queue()
            }
        }
    }

    private void handleRoleSelection(final StringSelectInteraction interaction, final Collection<Role> selectedRoles, final Guild guild) {
        final var member = interaction.member
        final var toAdd = new ArrayList<Role>(selectedRoles.size())
        final var toRemove = new ArrayList<Role>(selectedRoles.size())

        interaction.component.options
                .stream().map { guild.getRoleById(it.value) }
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

    static enum PanelMode {
        Buttons, SelectMenu
    }
    static enum PanelType {
        Normal
    }
}
