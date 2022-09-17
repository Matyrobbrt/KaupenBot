package com.matyrobbrt.kaupenbot.commands.api

import com.jagrosh.jdautilities.command.CommandClient
import net.dv8tion.jda.api.JDA

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

interface BotExtension {
    default void fillCommands(CommandManager manager, CommandClient client) {}
    default void subscribeEvents(JDA jda) {}
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface RegisterExtension {}