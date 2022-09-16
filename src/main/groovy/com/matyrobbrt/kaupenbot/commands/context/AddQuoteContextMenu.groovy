package com.matyrobbrt.kaupenbot.commands.context

import com.jagrosh.jdautilities.command.MessageContextMenu
import com.jagrosh.jdautilities.command.MessageContextMenuEvent
import com.matyrobbrt.kaupenbot.quote.Quote
import com.matyrobbrt.kaupenbot.quote.Quotes
import groovy.transform.CompileStatic

@CompileStatic
final class AddQuoteContextMenu extends MessageContextMenu {
    AddQuoteContextMenu() {
        name = 'Add Quote'
    }

    @Override
    protected void execute(MessageContextMenuEvent event) {
        if (!event.fromGuild) {
            event.replyProhibited('This command can only be used in guilds!').queue()
            return
        }

        final text = event.getTarget().getContentRaw()
        final guildId = event.getGuild().getIdLong()

        final finishedQuote = new Quote(text, event.target.author.id, event.user.id)

        final quoteID = Quotes.getQuoteSlot(guildId);
        finishedQuote.id = quoteID

        Quotes.addQuote(guildId, finishedQuote)

        event.replyEmbeds(embed {
            title = "Added quote $quoteID"
            description = finishedQuote.quote
        }).queue()
    }
}
