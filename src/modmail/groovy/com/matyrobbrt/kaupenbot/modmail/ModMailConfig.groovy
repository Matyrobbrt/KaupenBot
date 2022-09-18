package com.matyrobbrt.kaupenbot.modmail

import groovy.transform.CompileStatic
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@CompileStatic
@ConfigSerializable
class ModMailConfig {
    String[] prefixes = ['=']
    boolean anonymousByDefault = true
    boolean repliesOnly = true
    long loggingChannel
    long guildId
    String[] pingRoles
    long blacklistedRole
}
