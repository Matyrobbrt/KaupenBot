package com.matyrobbrt.kaupenbot.common.extension

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.api.PluginRegistry
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import net.dv8tion.jda.api.JDA
import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.jetbrains.annotations.Nullable

import java.lang.annotation.ElementType
import java.lang.annotation.Target
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Consumer
import java.util.function.Predicate

interface BotExtension {
    default void registerCommands(CommandManager manager, CommandClient client) {}
    default void subscribeEvents(JDA jda) {}
    default void registerPlugins(PluginRegistry registry) {}

    default void scheduleTasks(ScheduledExecutorService service) {}

    default void setupEndpoints(HttpServer server) {}
    default boolean usesWebServer() { false }
}

@Target([ElementType.TYPE])
@GroovyASTTransformationClass(value = 'com.matyrobbrt.kaupenbot.common.extension.ExtensionDiscoveryASTT')
@interface RegisterExtension {
    String botId()
    /**
     * The ID of the extension.
     */
    String value() default ''
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

@CompileStatic
final class ExtensionManager {
    private final Predicate<String> enabledPredicate
    ExtensionManager(Predicate<String> enabledPredicate) {
        this.enabledPredicate = enabledPredicate === null ? (Predicate<String>) (e) -> true : enabledPredicate
    }

    private final Map<String, BotExtension> namedExtensions = [:]
    private final List<BotExtension> unnamedExtensions = []

    @SuppressWarnings('unused')
    void register(@Nullable String id, BotExtension extension) {
        if (id === null) {
            unnamedExtensions.add(extension)
        } else {
            namedExtensions[id] = extension
        }
    }

    void forEachEnabled(Consumer<? extends BotExtension> consumer) {
        unnamedExtensions.forEach(consumer)
        namedExtensions.forEach { name, ext ->
            if (enabledPredicate.test(name))
                consumer.accept(ext)
        }
    }

    boolean anyEnabled(@ClosureParams(value = SimpleType, options = 'com.matyrobbrt.kaupenbot.common.extension.BotExtension') Closure predicate) {
        return unnamedExtensions.any(predicate) || namedExtensions.any { name, ext ->
            enabledPredicate.test(name) && predicate.call(ext)
        }
    }
}