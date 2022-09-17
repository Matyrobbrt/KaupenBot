package com.matyrobbrt.kaupenbot.extensions

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.commands.api.CommandManager
import net.dv8tion.jda.api.JDA

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

interface BotExtension {
    default void registerCommands(CommandManager manager, CommandClient client) {}
    default void subscribeEvents(JDA jda) {}
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface RegisterExtension {}