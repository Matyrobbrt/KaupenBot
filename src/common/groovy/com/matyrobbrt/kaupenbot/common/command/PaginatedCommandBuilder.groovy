package com.matyrobbrt.kaupenbot.common.command

import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.jdahelper.pagination.PaginatorBuilder
import com.matyrobbrt.kaupenbot.common.util.JavaCalls
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder

@CompileStatic
class PaginatedCommandBuilder extends CommandBuilder {
    private Paginator paginator
    private Closure<EmbedBuilder> factory

    void setPaginator(final PaginatorBuilder paginator, final boolean requireDefer = false) {
        if (requireDefer) paginator.buttonInteractionHandler(JavaCalls.deferringHandler(this))
        this.paginator = paginator
                .message((startingIndex, maximum, args) -> new MessageCreateBuilder()
                        .addEmbeds(factory.call(startingIndex, maximum, args).build()))
                .build()
    }
    Paginator getPaginator() {
        paginator
    }

    void embedFactory(@ClosureParams(
            value = SimpleType,
            options = ['java.lang.Integer', 'java.lang.Integer', 'java.util.List<java.lang.String>']
    ) Closure<EmbedBuilder> closure) {
        this.factory = closure
    }

    void sendPaginatedMessage(SlashCommandInteractionEvent event, final int maximum, final String... args) {
        createPaginatedMessage(event, maximum, args).queue()
    }

    ReplyCallbackAction createPaginatedMessage(SlashCommandInteractionEvent event, final int maximum, final String... args) {
        return createPaginatedMessage(event, 0, maximum, args)
    }

    ReplyCallbackAction createPaginatedMessage(SlashCommandInteractionEvent event, final int startingIndex, final int maximum, final String... args) {
        final var message = paginator.createPaginatedMessage(startingIndex, maximum, event.getUser().getIdLong(), args)
        return event.deferReply()
                .applyData(message)
    }

    int getPagesNumber(final int maximum) {
        (JavaCalls.mod(maximum, paginator.itemsPerPage) == 0) ? (JavaCalls.div(maximum, paginator.itemsPerPage)) : (JavaCalls.div(maximum, paginator.itemsPerPage) + 1)
    }

    int getPageNumber(final int startingIndex) {
        JavaCalls.div(startingIndex, paginator.itemsPerPage) + 1
    }
}
