package com.matyrobbrt.kaupenbot.commands.api

import net.dv8tion.jda.api.JDA

interface CommandExtension {
    void fillCommands(CommandManager manager)
    default void subscribeEvents(JDA jda) {}
}