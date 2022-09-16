package com.matyrobbrt.kaupenbot.commands.api


import groovy.transform.CompileStatic
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull
import java.util.function.Consumer

@CompileStatic
interface CommandManager {
    void addListener(String name, Consumer<SlashCommandInteractionEvent> consumer)
    void addCommand(CommandBuilder command)
    default void addCommand(@DelegatesTo(value = CommandBuilder, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        final builder = new CommandBuilder()
        closure.delegate = builder
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
        addCommand(builder)
    }
}

@CompileStatic
final class CommandManagerImpl implements EventListener, CommandManager {
    CommandManagerImpl(LocalizationFunction localization) {
        this.localization = localization
    }
    private final LocalizationFunction localization

    private final Map<String, CommandBuilder> commands = [:]
    private final Map<String, Consumer<SlashCommandInteractionEvent>> commandListeners = [:]

    @Override
    void onEvent(@NotNull @Nonnull GenericEvent event) {
        if (event instanceof SlashCommandInteractionEvent) {
            onSlashCommand(event)
        }
    }

    void upsert(JDA jda, List <CommandData> extraData) {
        List<CommandData> data = new ArrayList<>()

        // Build the command and privilege data
        for (final command : commands.values()) {
            data.add(command.build(localization))
        }
        jda.updateCommands()
                .addCommands(extraData)
                .addCommands(data)
                .queue()
    }

    @Override
    void addCommand(CommandBuilder command) {
        commands[command.name] = command
        recursivelyListen(command, '')
    }

    private void recursivelyListen(CommandBuilder command, String prefix) {
        addListener(prefix + command.name, command.listener)
        command.subcommands.each {
            recursivelyListen(it, prefix + command.name + '/')
        }
    }

    @Override
    void addListener(String name, Consumer<SlashCommandInteractionEvent> consumer) {
        commandListeners[name] = consumer
    }

    private void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        final listener = commandListeners[event.commandPath]
        if (listener !== null)
            listener.accept(event)
    }
}
