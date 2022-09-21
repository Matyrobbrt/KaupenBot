package com.matyrobbrt.kaupenbot.common.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.IEventManager;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.internal.JDAImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManagerWithFeedback implements IEventManager {
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void register(@NotNull Object listener) {
        if (!(listener instanceof EventListener)) {
            throw new IllegalArgumentException("Listener must implement EventListener");
        }
        listeners.add((EventListener) listener);
    }

    @Override
    public void unregister(@NotNull Object listener) {
        //noinspection SuspiciousMethodCalls
        listeners.remove(listener);
    }

    @Override
    public void handle(@NotNull GenericEvent event) {
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable throwable) {
                JDAImpl.LOG.error("One of the EventListeners had an uncaught exception", throwable);
                if (throwable instanceof Error)
                    throw (Error) throwable;
                if (event instanceof IReplyCallback cb) {
                    // Reply to the user in order to inform them
                    cb.deferReply(true)
                            .addEmbeds(new EmbedBuilder()
                                    .setTitle("This interaction failed due to an exception.")
                                    .setColor(Color.RED)
                                    .setDescription(throwable.toString())
                                    .build())
                            .setCheck(() -> !cb.isAcknowledged())
                            .queue();
                }
            }
        }
    }

    @NotNull
    @Override
    public List<Object> getRegisteredListeners() {
        return Collections.unmodifiableList(listeners);
    }
}
