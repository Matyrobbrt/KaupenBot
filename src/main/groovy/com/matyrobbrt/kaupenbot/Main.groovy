package com.matyrobbrt.kaupenbot

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction

import java.nio.file.Files
import java.nio.file.Path

@Slf4j
@CompileStatic
class Main {
    static void main(String[] args) throws IOException {
        MessageCreateAction.defaultMentionRepliedUser = false
        final env = Dotenv.load()
        if (env.get('enableModMail') === null || Boolean.parseBoolean(env.get('enableModMail'))) {
            final path = Path.of('modmail')
            Files.createDirectories(path)
            Class.forName('com.matyrobbrt.kaupenbot.modmail.ModMail')
                .getMethod('start', Path, Dotenv)
                .invoke(null, path, env)
        } else {
            log.warn('ModMail is disabled.')
        }
        KaupenBot.start(env)
    }
}
