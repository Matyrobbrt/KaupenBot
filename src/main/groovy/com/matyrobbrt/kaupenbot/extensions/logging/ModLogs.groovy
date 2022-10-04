package com.matyrobbrt.kaupenbot.extensions.logging

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.audit.ActionType

import javax.annotation.Nullable
import java.util.concurrent.TimeUnit

@CompileStatic
final class ModLogs {
    private static final Cache<Action, ActionData> DATA = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .expireAfterAccess(1, TimeUnit.SECONDS)
        .build()

    @Nullable
    static ActionData getData(ActionType actionType, long target) {
        DATA.getIfPresent(new Action(actionType, target))
    }

    static void putData(ActionType actionType, long target, long moderator, String reason) {
        DATA.put(new Action(actionType, target), new ActionData(moderator, reason))
    }

    @CompileStatic
    static record Action(ActionType actionType, long target) {}
    @CompileStatic
    static record ActionData(long moderator, String reason) {}
}
