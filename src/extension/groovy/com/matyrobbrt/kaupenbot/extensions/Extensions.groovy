package com.matyrobbrt.kaupenbot.extensions

import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder

import java.time.Instant

@CompileStatic
class Extensions {
    static EmbedBuilder sentNow(EmbedBuilder self) {
        return self.setTimestamp(Instant.now())
    }
}
