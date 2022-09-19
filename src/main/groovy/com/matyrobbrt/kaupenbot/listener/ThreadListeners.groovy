package com.matyrobbrt.kaupenbot.listener

import com.matyrobbrt.kaupenbot.KaupenBot
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.ThreadChannel
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
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

            final message = 'Oh hey, a new thread 👀! Better get the mods in here.'
            final thread = event.channel.asThreadChannel()
            thread.join()
                .flatMap { thread.sendMessage(message) }
                .delay(1, TimeUnit.SECONDS)
                .flatMap { it.editMessage("$message\nHey, <@&${KaupenBot.config.moderatorRole}>! Squirrel!").setAllowedMentions([Message.MentionType.ROLE]) }
                .delay(2, TimeUnit.SECONDS)
                .flatMap { it.delete() }
                .queue()
        }
    }
}
