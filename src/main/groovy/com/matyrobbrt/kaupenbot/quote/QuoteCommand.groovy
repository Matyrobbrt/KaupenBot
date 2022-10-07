package com.matyrobbrt.kaupenbot.quote

import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.components.Component
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.command.PaginatedSlashCommand
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

import java.awt.*
import java.time.Instant
import java.util.List

@CompileStatic
final class QuoteCommand extends SlashCommand {
    QuoteCommand() {
        name = 'quote'
        guildOnly = true
        children = new SlashCommand[] {
            new AddCommand(), new GetCommand(), new ListCommand(), new RemoveCommand()
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}

@PackageScope(PackageScopeTarget.CLASS) @CompileStatic
final class AddCommand extends SlashCommand {

    AddCommand() {
        name = 'add'
        help = 'Add a new Quote.'
        guildOnly = true
        options = [
                new OptionData(OptionType.STRING, "quote", 'The text of the quote.').setRequired(true),
                new OptionData(OptionType.USER, "quotee", 'The person being quoted. Mutually exclusive with quoteetext.').setRequired(false),
                new OptionData(OptionType.STRING, "quoteetext", 'The thing being quoted. Mutually exclusive with quotee.').setRequired(false)
        ]
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final var guildId = event.getGuild().getIdLong()
        final var text = event.string('quote')
        final var quoteeUser = event.getOption('quotee')
        final var quoteeText = event.getOption('quoteetext')

        // Verify that there's a message being quoted.
        if (quoteeText != null && quoteeUser != null) {
            event.reply('Cannot add a quote with a quoted user and quoted text. Choose one.').setEphemeral(true).queue()
            return
        }


        Quote finishedQuote

        // Check if there's any attribution
        if (quoteeUser == null && quoteeText == null) {
            // Anonymous quote.
            finishedQuote = new Quote(text, 'Anonymous', event.user.id, Instant.now())
        } else {
            if (quoteeUser != null) {
                var id = quoteeUser.asUser.id
                if (id == event.user.id) {
                    event.replyProhibited('You cannot quote yourself!').queue()
                    return
                }
                finishedQuote = new Quote(text, id, event.user.id, Instant.now())
            } else {
                // No user ID. Must be a string assignment.
                finishedQuote = new Quote(text, quoteeText.asString, event.user.id, Instant.now())
            }
        }

        var quoteID = Quotes.getQuoteSlot(guildId)
        finishedQuote.id = quoteID

        // All execution leads to here, where finishedQuote is valid.
        Quotes.addQuote(guildId, finishedQuote)

        event.reply("Added quote ${quoteID + 1}!").mentionRepliedUser(false).queue()
    }
}

@PackageScope(PackageScopeTarget.CLASS) @CompileStatic
final class GetCommand extends SlashCommand {

    GetCommand() {
        name = 'get'
        help = 'Get a quote. Specify a number if you like, otherwise a random is chosen.'
        guildOnly = true

        options = Collections.singletonList(new OptionData(OptionType.INTEGER, 'index', 'The index of the quote to fetch.'))
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        final var guildId = event.getGuild().getIdLong()
        final var index = event.getOption('index');

        // Check whether any parameters given.
        if (index != null) {
            // We have something to parse.
            if (index.asInt > Quotes.getQuoteSlot(guildId)) {
                event.reply(Quotes.QUOTE_NOT_PRESENT).mentionRepliedUser(false).queue()
                return
            }

            var fetched = Quotes.getQuote(guildId, index.asInt - 1)
            // Check if the quote exists.
            if (fetched == Quotes.NULL) {
                // Send the standard message
                event.reply(Quotes.QUOTE_NOT_PRESENT).mentionRepliedUser(false).queue()
                return
            }

            // It exists, so get the content and send it.
            assert fetched != null
            event.reply(fetched.resolve(event.getJDA()))
                    .mentionRepliedUser(false).queue()
            return
        }

        Quote fetched
        Random rand = new Random()
        do {
            int id = rand.nextInt(Quotes.getQuoteSlot(guildId))
            fetched = Quotes.getQuote(guildId, id)
        } while (fetched == null)

        // It exists, so get the content and send it.
        event.reply(fetched.resolve(event.getJDA())).mentionRepliedUser(false).queue();
    }
}

@PackageScope(PackageScopeTarget.CLASS) @CompileStatic
final class ListCommand extends PaginatedSlashCommand {

    ListCommand() {
        super(KaupenBot.paginator('list-quotes-cmd')
                .itemsPerPage(10)
                .dismissible(true)
                .buttonsOwnerOnly(true)
                .lifespan(Component.Lifespan.TEMPORARY)
                .buttonFactory(
                        Paginator.DEFAULT_BUTTON_FACTORY.with(Paginator.ButtonType.DISMISS, id -> Button.secondary(id, Emoji.fromUnicode('\uD83D\uDEAE')))
                )
                .buttonOrder(Paginator.ButtonType.FIRST, Paginator.ButtonType.PREVIOUS, Paginator.ButtonType.DISMISS, Paginator.ButtonType.NEXT, Paginator.ButtonType.LAST)
        )
        name = 'list'
        help = 'Get all quotes'
        guildOnly = true
        options = List.of(
                new OptionData(OptionType.INTEGER, 'page', 'The index of the page to display. 1 if not specified.')
        )
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        final var guildId = event.getGuild().getIdLong()
        final var pgIndex = event.integer('page', 1)
        final var startingIndex = (pgIndex - 1) * getItemsPerPage()
        final var maximum = Quotes.getQuotesForGuild(guildId).size()
        if (maximum <= startingIndex) {
            event.deferReply().setContent("The page index provided ($pgIndex) was too big! There are only ${getPagesNumber(maximum)} pages.").queue()
            return
        }
        createPaginatedMessage(event, startingIndex, maximum, event.guild.id).queue()
    }

    @Override
    protected EmbedBuilder getEmbed(final int start, final int maximum, final List<String> arguments) {
        EmbedBuilder embed
        // We have to make sure that this doesn't crash if we list a fresh bot.
        final long guildId = Long.parseLong(arguments.get(0));
        if (Quotes.getQuoteSlot(guildId) == 0) {
            embed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setDescription('There are no quotes!')
                    .setTimestamp(Instant.now())
        } else {
            embed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Quote Page ${getPageNumber(start)}/${getPagesNumber(maximum)}")
                    .setTimestamp(Instant.now())
        }

        // From the specified starting point until the end of the page.
        for (int x = start; x < start + getItemsPerPage(); x++) {
            // But stop early if we hit the end of the list,
            if (x >= Quotes.getQuoteSlot(guildId)) {
                break
            }

            // Get the current Quote
            Quote fetchedQuote = Quotes.getQuote(guildId, x)

            embed.appendDescription("**${x + 1}**: ")
            if (fetchedQuote == Quotes.NULL || fetchedQuote == null) {
                embed.appendDescription('Quote does not exist.')
            } else {
                // Put it in the description.
                // message - author
                embed.appendDescription("${fetchedQuote.quote} - ${fetchedQuote.resolveQuotee(KaupenBot.jda)}")
            }
            if (x - start != itemsPerPage - 1) {
                embed.appendDescription('\n')
            }
        }

        return embed
    }
}

@PackageScope(PackageScopeTarget.CLASS) @CompileStatic
final class RemoveCommand extends SlashCommand {

    RemoveCommand() {
        name = 'remove'
        help = 'Remove a quote from the list.'
        guildOnly = true

        options = Collections.singletonList(new OptionData(OptionType.INTEGER, 'index', 'The index of the quote to delete').setRequired(true))
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        final var index = event.integer('index', 1)
        final quote = Quotes.getQuote(event.guild.idLong, index - 1)
        if (quote === null) {
            event.replyProhibited("Quote with ID $index was not found!").queue()
            return
        }
        if (!event.member.hasPermission(Permission.MESSAGE_MANAGE) && quote.author != event.user.id) {
            event.replyProhibited('You cannot remove this quote!').queue()
            return
        }
        Quotes.removeQuote(event.getGuild().getIdLong(), index - 1)
        event.reply("Quote $index removed.").mentionRepliedUser(false).queue()
    }
}