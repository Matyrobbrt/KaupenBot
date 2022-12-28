package com.matyrobbrt.kaupenbot.listener

import com.matyrobbrt.kaupenbot.KaupenBot
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.RestAction
import org.jetbrains.annotations.NotNull

import java.util.concurrent.TimeUnit

@CompileStatic
final class ThreadListeners extends ListenerAdapter {
    private final List<Long> createdThreads = new LinkedList<>()
    @Override
    void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (event.channel instanceof ThreadChannel) {
            if (event.channel.idLong in createdThreads) return
            createdThreads.add(event.channel.idLong)

            final thread = event.channel.asThreadChannel()
            if (thread.parentChannel.type === ChannelType.FORUM) return // The HelpChannels extensions will handle forum posts

            addMods(thread).queue()
        }
    }

    static RestAction<Void> addMods(ThreadChannel channel) {
        final message = 'Oh hey, a new thread 👀! Better get the mods in here.'
        channel.sendMessage(message)
                .delay(1, TimeUnit.SECONDS)
                .flatMap { it.editMessage("$message\nHey, <@&${KaupenBot.config.moderatorRole}>! Squirrel!").setAllowedMentions([Message.MentionType.ROLE]) }
                .delay(2, TimeUnit.SECONDS)
                .flatMap { it.delete() }
    }
}
