package com.matyrobbrt.kaupenbot.extensions;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SecondParam;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.hooks.EventListener;

public class JavaExtensions {

    @SuppressWarnings("rawtypes")
    public static <T extends Event> void subscribe(JDA self, @DelegatesTo.Target("event") Class<T> eventType, @DelegatesTo(
            target = "event", strategy = Closure.DELEGATE_FIRST, genericTypeIndex = 0
    ) @ClosureParams(
            value = SecondParam.FirstGenericType.class
    ) Closure closure) {
        self.addEventListener((EventListener) (event) -> {
            if (eventType.isInstance(event)) {
                closure.setDelegate(event);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                closure.call(event);
            }
        });
    }

}