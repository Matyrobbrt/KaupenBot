package com.matyrobbrt.kaupenbot.api.plugins

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.SlashCommand
import com.matyrobbrt.kaupenbot.api.Plugin
import groovy.transform.CompileStatic

@CompileStatic
interface CommandsPlugin extends Plugin {
    void addCommand(Command command)
    void addSlashCommand(SlashCommand command)
}