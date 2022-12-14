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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction

import java.util.function.Consumer
import java.util.function.Predicate

@CompileStatic
sealed class CommandBuilder permits PaginatedCommandBuilder {
    String name
    String description = '.'
    boolean guildOnly
    List<OptionData> options = []
    @PackageScope final List<CommandBuilder> subcommands = []
    @PackageScope final Map<String, CommandBuilder> groups = [:]
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

    void setRequire(Permission permission) {
        requiredPermissions.add(permission)
    }

    void checkIf(Predicate<SlashCommandInteractionEvent> predicate, String failureMessage = null) {
        predicates.add(com.matyrobbrt.kaupenbot.common.util.JavaCalls.slashPredicate(predicate, failureMessage))
    }
    void failIf(Predicate<SlashCommandInteractionEvent> predicate, String failureMessage = null) {
        checkIf(Predicate.not(predicate), failureMessage)
    }

    void checkHierarchy(String optionName) {
        checkIf({
            final user = it.getOption(optionName)?.asMember
            if (user !== null && it.member !== null) {
                if (!it.member.canInteract(user)) {
                    it.replyProhibited('Cannot interact with members ranked the same or higher than you.').queue()
                    return false
                } else if (user == it.member) {
                    it.replyProhibited('Cannot interact with yourself.').queue()
                    return false
                } else if (!it.guild.selfMember.canInteract(user) || user.idLong == it.guild.selfMember.idLong) {
                    it.replyProhibited('Cannot interact with members ranked the same or higher than the bot.').queue()
                    return false
                }
            }
            return true
        }, null)
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
    ) @ClosureParams(value = SimpleType, options = 'java.lang.String') Closure closure) {
        autoCompleteOptions([optionName], closure)
    }

    void autoCompleteOptions(List<String> optionNames, @DelegatesTo(
            value = CommandAutoCompleteInteractionEvent,
            strategy = Closure.DELEGATE_FIRST
    ) @ClosureParams(value = SimpleType, options = 'java.lang.String') Closure closure) {
        final optionsSet = new HashSet<>(optionNames)
        final oldAutoComplete = this.autoComplete
        this.autoComplete {
            if (oldAutoComplete !== null) {
                //noinspection UnnecessaryQualifiedReference
                oldAutoComplete.resolveStrategy = Closure.DELEGATE_FIRST
                oldAutoComplete.delegate = it
                oldAutoComplete(it)
            }
            if (optionsSet.contains(focusedOption?.name)) {
                closure.resolveStrategy = Closure.DELEGATE_FIRST
                closure.delegate = it
                closure(focusedOption.value)
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

    void group(@DelegatesTo(value = CommandBuilder, strategy = Closure.DELEGATE_FIRST) Closure group) {
        final builder = new CommandBuilder()
        group.delegate = builder
        group.resolveStrategy = Closure.DELEGATE_FIRST
        group()
        this.groups[builder.name] = builder
    }

    SlashCommandData build(LocalizationFunction localizationFunction) {
        final command = Commands.slash(name, description)
        command.setGuildOnly(guildOnly)
        if (!requiredPermissions.isEmpty())
            command.setDefaultPermissions(DefaultMemberPermissions.enabledFor(requiredPermissions))
        command.setLocalizationFunction(localizationFunction)
        subcommands.each {
            command.addSubcommands(it.asSubCommand())
        }
        if (!options.isEmpty()) {
            command.addOptions(options)
        }
        groups.forEach { name, group ->
            final data = new SubcommandGroupData(name, '.')
            group.subcommands.each {
                data.addSubcommands(it.asSubCommand())
            }
            command.addSubcommandGroups(data)
        }
        return command
    }

    SubcommandData asSubCommand() {
        final subcommandData = new SubcommandData(name, description)
        if (!options.isEmpty()) {
            subcommandData.addOptions(options)
        }
        return subcommandData
    }
}
