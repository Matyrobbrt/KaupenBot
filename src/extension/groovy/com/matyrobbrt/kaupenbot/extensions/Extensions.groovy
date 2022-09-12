package com.matyrobbrt.kaupenbot.extensions

import com.jagrosh.jdautilities.command.SlashCommandEvent
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.time.Instant

@CompileStatic
class Extensions {
    static EmbedBuilder sentNow(EmbedBuilder self) {
        return self.setTimestamp(Instant.now())
    }

    static <T> boolean none(Iterator<T> self, @ClosureParams(FirstParam.FirstGenericType.class) Closure predicate) {
        BooleanClosureWrapper bcw = new BooleanClosureWrapper(predicate)
        while (self.hasNext()) {
            if (bcw.call(self.next())) return false
        }
        return true
    }
    static <T> boolean none(Iterable<T> self, @ClosureParams(FirstParam.FirstGenericType.class) Closure predicate) {
        return none(self.iterator(), predicate)
    }

    @NotNull
    static String string(final SlashCommandEvent event, final String name) {
        return event.getOption(name)?.asString ?: ''
    }
    @Nullable
    static User user(final SlashCommandEvent event, final String name) {
        return event.getOption(name)?.asUser
    }

    @Nullable
    static Member getAt(Guild self, @Nonnull UserSnowflake user) {
        self.retrieveMember(user).submit(true).get()
    }
}
