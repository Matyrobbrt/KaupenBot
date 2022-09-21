package com.matyrobbrt.kaupenbot.quote

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import groovy.transform.CompileStatic

@CompileStatic
@RegisterExtension(value = 'quotes', botId = 'kbot')
final class QuotesExtension implements BotExtension {
    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        client.addSlashCommand(new QuoteCommand())
        client.addContextMenu(new AddQuoteContextMenu())
    }
}
