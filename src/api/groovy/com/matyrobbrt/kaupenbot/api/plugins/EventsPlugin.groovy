package com.matyrobbrt.kaupenbot.api.plugins

import com.matyrobbrt.kaupenbot.api.Plugin
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.hooks.EventListener

@CompileStatic
interface EventsPlugin extends Plugin {
    void addEventListener(EventListener listener)

    <E extends Event> void addEventListener(Class<E> eventType, @ClosureParams(value = FirstParam.FirstGenericType) Closure listener)
}