package com.matyrobbrt.kaupenbot.quote

import com.jagrosh.jdautilities.command.MessageContextMenu
import com.jagrosh.jdautilities.command.MessageContextMenuEvent
import groovy.transform.CompileStatic

import java.time.Instant

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

        final finishedQuote = new Quote(text, event.target.author.id, event.user.id, Instant.now())

        final quoteID = Quotes.getQuoteSlot(guildId)
        finishedQuote.id = quoteID

        Quotes.addQuote(guildId, finishedQuote)

        event.replyEmbeds(embed {
            title = "Added quote ${quoteID + 1}"
            description = finishedQuote.quote
        }).queue()
    }
}
