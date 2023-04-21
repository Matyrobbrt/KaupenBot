package com.matyrobbrt.kaupenbot.logparser.parsers

import com.matyrobbrt.kaupenbot.logparser.Log
import com.matyrobbrt.kaupenbot.logparser.LogParser
import com.matyrobbrt.kaupenbot.logparser.LogType
import com.matyrobbrt.kaupenbot.logparser.ResultHandler
import groovy.transform.CompileStatic

import java.util.function.BiFunction
import java.util.stream.Stream

@CompileStatic
class EnvParser implements LogParser {
    @Override
    void handle(Log log, BiFunction<Integer, Integer, String> gistLineGetter, ResultHandler handler) {
        try {
            final info = switch (log.type) {
                case LogType.CRASH_LOG -> handleCrash(log)
                case LogType.LOG -> handleLog(log)
            }

            handler.embed.addField('Environment Information', """
            Minecraft Version: ${info.minecraftVersion()}
            Environment: ${info.environmentType()}
            Forge Version: ${info.forgeVersion()}
            Java Version: ${info.javaVersion()}
            """.toString().trim(), false)
        } catch (Exception ignored) {
            handler.embed.addField('Environment Information', '**Could not parse**', false)
        }
    }

    static EnvInfo handleLog(Log log) {
        final args = Stream.of(log.lines[0].split(',')).map(String.&trim).toList()
        final l1 = log.lines[1]
        final javaVer = l1.substring(l1.indexOf('java version ') + 'java version '.length(), l1.indexOf(';'))
        new EnvInfo(
                args[args.indexOf('--fml.forgeVersion') + 1],
                args[args.indexOf('--fml.mcVersion') + 1],
                args[1].drop('forge'.length()).capitalize(),
                javaVer.replace(' by ', ', ')
        )
    }

    static EnvInfo handleCrash(Log log) {
        final details = log.details
        new EnvInfo(
                details['Forge']?.get(0)?.split(':')?[1] ?: details['Mod List'].stream()
                    .filter { it.containsIgnoreCase('|forge') }
                    .findFirst().orElseThrow().split('\\|')[3].trim(),
                details['Minecraft Version'][0],
                details['Type'][0].split(' ')[0],
                details['Java Version'][0]
        )
    }
}

record EnvInfo(String forgeVersion, String minecraftVersion, String environmentType, String javaVersion) {

}
