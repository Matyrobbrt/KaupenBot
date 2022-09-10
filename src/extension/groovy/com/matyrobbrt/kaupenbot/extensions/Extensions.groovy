package com.matyrobbrt.kaupenbot.extensions

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import net.dv8tion.jda.api.EmbedBuilder
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper

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
}
