//file:noinspection UnnecessaryQualifiedReference
package com.matyrobbrt.kaupenbot.common.command

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction

import java.util.function.Consumer
import java.util.function.Predicate

@CompileStatic
final class CommandBuilder {
    String name
    String description
    boolean guildOnly
    List<OptionData> options = []
    @PackageScope final List<CommandBuilder> subcommands = []
    final List<Permission> requiredPermissions = []
    final List<Predicate<SlashCommandInteractionEvent>> predicates = []
    Closure callback
    Closure autoComplete

    Consumer<SlashCommandInteractionEvent> getListener() {
        com.matyrobbrt.kaupenbot.common.util.JavaCalls.slashCallback(predicates, callback)
    }

    void require(Permission permission) {
        requiredPermissions.add(permission)
    }

    void checkIf(Predicate<SlashCommandInteractionEvent> predicate, String failureMessage = null) {
        predicates.add(com.matyrobbrt.kaupenbot.common.util.JavaCalls.slashPredicate(predicate, failureMessage))
    }
    void failIf(Predicate<SlashCommandInteractionEvent> predicate, String failureMessage = null) {
        checkIf(Predicate.not(predicate), failureMessage)
    }

    void action(@DelegatesTo(
            value = SlashCommandInteractionEvent,
            strategy = Closure.DELEGATE_FIRST
    ) @ClosureParams(value = SimpleType, options = 'net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent') Closure closure) {
        this.callback = closure
    }

    void autoComplete(@DelegatesTo(
            value = CommandAutoCompleteInteractionEvent,
            strategy = Closure.DELEGATE_FIRST
    ) @ClosureParams(value = SimpleType, options = 'net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent') Closure closure) {
        this.autoComplete = closure
    }
    void autoCompleteOption(String optionName, @DelegatesTo(
            value = CommandAutoCompleteInteractionEvent,
            strategy = Closure.DELEGATE_FIRST
    ) @ClosureParams(value = SimpleType, options = ['java.lang.String', 'net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent']) Closure closure) {
        final oldAutoComplete = this.autoComplete
        this.autoComplete {
            if (oldAutoComplete !== null) {
                //noinspection UnnecessaryQualifiedReference
                oldAutoComplete.resolveStrategy = Closure.DELEGATE_FIRST
                oldAutoComplete.delegate = it
                oldAutoComplete(it)
            }
            if (focusedOption?.name == optionName) {
                closure.resolveStrategy = Closure.DELEGATE_FIRST
                closure.delegate = it
                closure(focusedOption.value, it)
            }
        }
    }

    void subCommand(@DelegatesTo(value = CommandBuilder, strategy = Closure.DELEGATE_FIRST) Closure subCommand) {
        final builder = new CommandBuilder()
        subCommand.delegate = builder
        subCommand.resolveStrategy = Closure.DELEGATE_FIRST
        subCommand()
        subcommands.add(builder)
    }

    SlashCommandData build(LocalizationFunction localizationFunction) {
        final command = Commands.slash(name, description)
        command.setGuildOnly(guildOnly)
        if (!requiredPermissions.isEmpty())
            command.setDefaultPermissions(DefaultMemberPermissions.enabledFor(requiredPermissions))
        command.setLocalizationFunction(localizationFunction)
        subcommands.each {
            final subcommandData = new SubcommandData(it.name, it.description)
            if (!it.options.isEmpty()) {
                subcommandData.addOptions(it.options)
            }
            command.addSubcommands(subcommandData)
        }
        if (!options.isEmpty()) {
            command.addOptions(options)
        }
        return command
    }
}
