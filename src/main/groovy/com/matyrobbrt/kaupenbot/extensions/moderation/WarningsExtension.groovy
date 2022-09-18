package com.matyrobbrt.kaupenbot.extensions.moderation

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.api.PluginRegistry
import com.matyrobbrt.kaupenbot.apiimpl.plugins.WarningPluginImpl
import com.matyrobbrt.kaupenbot.commands.moderation.WarnCommand
import com.matyrobbrt.kaupenbot.commands.moderation.WarningCommand
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import groovy.transform.CompileStatic

@CompileStatic
@RegisterExtension(botId = 'kbot')
final class WarningsExtension implements BotExtension {
    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        client.addSlashCommand(new WarningCommand())
        client.addCommand(new WarnCommand())
    }

    @Override
    void registerPlugins(PluginRegistry registry) {
        registry.registerPlugin('warnings', new WarningPluginImpl())
    }
}
