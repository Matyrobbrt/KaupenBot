package com.matyrobbrt.kaupenbot.util

import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.jdahelper.pagination.PaginatorBuilder
import com.matyrobbrt.jdahelper.pagination.PaginatorImpl
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder

@CompileStatic
abstract class PaginatedSlashCommand extends SlashCommand {
    public final Paginator paginator

    PaginatedSlashCommand(final PaginatorBuilder paginator) {
        this.paginator = paginator
                .message((startingIndex, maximum, args) -> new MessageCreateBuilder()
                    .addEmbeds(getEmbed(startingIndex, maximum, args).build()))
                .buttonInteractionHandler(JavaCalls.deferringHandler(this))
                .build()
    }

    protected int getItemsPerPage() {
        return paginator.getItemsPerPage();
    }

    /**
     * Given the index of the start of the embed, get the next {@linkplain PaginatedSlashCommand#getItemsPerPage()} items.
     *
     * @param startingIndex the index of the first item in the list.
     * @param maximum the maximum amount of items to be displayed
     * @param arguments the arguments of the button
     * @return an unbuilt embed that can be sent.
     */
    protected abstract EmbedBuilder getEmbed(int startingIndex, int maximum, final List<String> arguments)

    protected ReplyCallbackAction createPaginatedMessage(SlashCommandEvent event, final int maximum, final String... args) {
        return createPaginatedMessage(event, 0, maximum, args)
    }

    protected ReplyCallbackAction createPaginatedMessage(SlashCommandEvent event, final int startingIndex, final int maximum, final String... args) {
        final var message = paginator.createPaginatedMessage(startingIndex, maximum, event.getUser().getIdLong(), args)
        return event.deferReply()
                .applyData(message)
    }

    protected int getPagesNumber(final int maximum) {
        (JavaCalls.mod(maximum, itemsPerPage) == 0) ? (JavaCalls.div(maximum, itemsPerPage)) : (JavaCalls.div(maximum, itemsPerPage) + 1)
    }

    protected int getPageNumber(final int startingIndex) {
        JavaCalls.div(startingIndex, itemsPerPage) + 1
    }
}
