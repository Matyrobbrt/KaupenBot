package com.matyrobbrt.kaupenbot.apiimpl.plugins

import com.matyrobbrt.kaupenbot.api.plugins.EventsPlugin
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.annotation.Nonnull

@CompileStatic
final class EventsPluginImpl extends BasePlugin implements EventListener, EventsPlugin {

    private final List<Listener> listeners = []

    @Override
    void scriptUnloaded(UUID id) {
        listeners.removeIf { it.owner == id }
    }

    @Override
    void addEventListener(EventListener listener) {
        listeners.add(new Listener(listener, currentScript.get()))
    }

    @Override
    <E extends Event> void addEventListener(Class<E> eventType, @ClosureParams(value = FirstParam.FirstGenericType) Closure listener) {
        listeners.add(new Listener(new EventListener() {
            @Override
            void onEvent(@NotNull @Nonnull GenericEvent event) {
                if (eventType.isInstance(event)) {
                    listener((E) event)
                }
            }
        }, currentScript.get()))
    }

    @Override
    void onEvent(@NotNull @Nonnull GenericEvent event) {
        listeners.forEach { it.@listener.onEvent(event) }
    }

    @CompileStatic
    @TupleConstructor
    static final class Listener {
        EventListener listener
        @Nullable
        UUID owner
    }
}