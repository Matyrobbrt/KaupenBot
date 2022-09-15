package com.matyrobbrt.kaupenbot.apiimpl.plugins

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandClient
import com.jagrosh.jdautilities.command.SlashCommand
import com.matyrobbrt.kaupenbot.api.plugins.CommandsPlugin

final class CommandsPluginImpl extends BasePlugin implements CommandsPlugin {
    private final CommandClient client
    private final Map<UUID, List<String>> commandNames = [:]

    CommandsPluginImpl(CommandClient client) {
        this.client = client
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
}
