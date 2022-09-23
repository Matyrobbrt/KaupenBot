package com.matyrobbrt.kaupenbot.extensions.misc

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.commands.EvalCommand
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.tricks.AddTrickCommand
import com.matyrobbrt.kaupenbot.tricks.RunTrickCommand
import com.matyrobbrt.kaupenbot.tricks.TrickCommand
import com.matyrobbrt.kaupenbot.tricks.Tricks
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget

@CompileStatic
@PackageScope([PackageScopeTarget.CLASS])
@RegisterExtension(value = 'tricks', botId = 'kbot')
final class TricksExtension implements BotExtension {
    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        new EvalCommand().tap {
            client.addSlashCommand(it)
            client.addCommand(it)
        }
        // TODO slash command with modal for add trick
        client.addCommand(new AddTrickCommand())

        client.addSlashCommand(new TrickCommand())

        Tricks.getTricks().forEach { tr ->
            client.addCommand(new RunTrickCommand.Prefix(tr))
        }
    }
}
