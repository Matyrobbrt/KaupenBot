package com.matyrobbrt.kaupenbot

import com.matyrobbrt.kaupenbot.modmail.ModMail
import groovy.transform.CompileStatic
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class Main {
    static void main(String[] args) throws IOException {
        MessageCreateAction.defaultMentionRepliedUser = false
        final env = Dotenv.load()
        if (env.get('enableModMail') ?: true) {
            final path = Path.of('modmail')
            Files.createDirectories(path)
            ModMail.start(path, env)
        }
        KaupenBot.start(env)
    }
}
