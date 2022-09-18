package com.matyrobbrt.kaupenbot.common.extension

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.api.PluginRegistry
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import net.dv8tion.jda.api.JDA
import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Target

interface BotExtension {
    default void registerCommands(CommandManager manager, CommandClient client) {}
    default void subscribeEvents(JDA jda) {}
    default void registerPlugins(PluginRegistry registry) {}
}

@Target([ElementType.TYPE])
@GroovyASTTransformationClass(value = 'com.matyrobbrt.kaupenbot.common.extension.ExtensionDiscoveryASTT')
@interface RegisterExtension {
    String botId()
}

@Target(ElementType.METHOD)
@GroovyASTTransformationClass(value = 'com.matyrobbrt.kaupenbot.common.extension.ExtensionFinderASTT')
@interface ExtensionFinder {
    String value()
}

@Target(ElementType.PARAMETER)
@interface ExtensionArgument {
    String value()
}