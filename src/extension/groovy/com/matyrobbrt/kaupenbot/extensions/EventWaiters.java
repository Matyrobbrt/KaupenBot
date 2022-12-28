package com.matyrobbrt.kaupenbot.extensions;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;

import java.util.HashMap;
import java.util.Map;

public final class EventWaiters {
    private static final Map<JDA, EventWaiter> WAITERS = new HashMap<>();

    public static void registerWaiter(JDA self, EventWaiter waiter) {
        WAITERS.put(self, waiter);
        self.addEventListener(waiter);
    }

    public static EventWaiter getWaiter(JDA self) { return WAITERS.get(self); }
}
