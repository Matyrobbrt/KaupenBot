package com.matyrobbrt.kaupenbot.apiimpl.plugins

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandClient
import com.jagrosh.jdautilities.command.SlashCommand
import com.matyrobbrt.kaupenbot.api.plugins.CommandsPlugin
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import groovy.transform.CompileStatic

@CompileStatic
final class CommandsPluginImpl extends BasePlugin implements CommandsPlugin {
    private final CommandClient client
    private final CommandManager manager
    private final Map<UUID, List<String>> commandNames = [:]

    CommandsPluginImpl(CommandClient client, CommandManager manager) {
        this.client = client
        this.manager = manager
    }

    @Override
    void scriptUnloaded(UUID id) {
        commandNames[id]?.forEach(client.&removeCommand)
    }

    @Override
    void addCommand(Command command) {
        client.addCommand(command)
        commandNames.computeIfAbsent(currentScript.get(), { new ArrayList<>() }).add(command.name)
    }

    @Override
    void addSlashCommand(SlashCommand command) {
        client.addSlashCommand(command)
    }

    CommandManager getManager() { manager }
}
