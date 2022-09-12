package com.matyrobbrt.kaupenbot.listener

import com.matyrobbrt.kaupenbot.KaupenBot
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.annotations.NotNull

import java.util.concurrent.TimeUnit

@CompileStatic
class ThreadListeners extends ListenerAdapter {
    @Override
    void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (event.channel.type == ChannelType.GUILD_PUBLIC_THREAD) {
            final thread = event.channel.asThreadChannel()
            thread.join()
                .flatMap { thread.sendMessage('New thread has been created!') }
                .delay(1, TimeUnit.SECONDS)
                .flatMap { it.editMessage("<@&${KaupenBot.config.moderatorRole}> you may want to check out this thread!") }
                .delay(2, TimeUnit.SECONDS)
                .flatMap { it.delete() }
                .queue()
        }
    }
}
