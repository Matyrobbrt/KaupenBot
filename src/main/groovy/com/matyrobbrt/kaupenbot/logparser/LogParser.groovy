package com.matyrobbrt.kaupenbot.logparser

import com.matyrobbrt.kaupenbot.logparser.parsers.EnvParser
import com.matyrobbrt.kaupenbot.logparser.parsers.ExceptionParsers
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import org.jetbrains.annotations.Nullable

import java.util.function.BiFunction

@CompileStatic
interface LogParser {
    void handle(Log log, BiFunction<Integer, Integer, String> gistLineGetter, ResultHandler handler)
}

@CompileStatic
interface ResultHandler {
    void appendIssue(MessageEmbed embed, String title)

    EmbedBuilder getEmbed()
}

@CompileStatic
final class Log {
    public static final List<LogParser> PARSERS = List.of(new EnvParser(), ExceptionParsers.default()) as List<LogParser>

    public static final int AT_LENGTH = '\tat '.length()
    public static final int CAUSED_BY_LENGTH = 'Caused by: '.length()

    final LogType type
    final List<String> lines

    Log(List<String> lines) {
        this(LogType.detectType(lines[0]), lines)
    }

    Log(LogType type, List<String> lines) {
        this.lines = lines
        this.type = type
    }

    @Lazy
    List<LogException> exceptions = {
        final List<LogException> exceptions = []

        LogException ex = null
        for (int i = 0; i < lines.size(); i++) {
            final line = lines.get(i)
            if (line.startsWith('\tat ')) {
                if (ex !== null) {
                    ex.stackTrace.add(line.drop(AT_LENGTH))
                } else {
                    ex = new LogException()
                    ex.start = i - 1
                    ex.theException = lines[i - 1]
                    exceptions.add(ex)
                }
            } else if (line.startsWith('Caused by: ')) {
                if (ex !== null) {
                    ex.end = i - 1
                    final cb = new LogException()
                    ex.causedBy = cb
                    ex = cb

                    ex.start = i
                    ex.theException = line.drop(CAUSED_BY_LENGTH)
                }
            } else if (ex !== null) {
                ex.end = i - 1
                ex = null
            }
        }

        if (type === LogType.CRASH_LOG) {
            exceptions.removeIf {
                it.exceptionStr[1].empty
            }
        }

        return exceptions
    }()

    @Lazy Map<String, List<String>> details = {
        final Map<String, List<String>> details = [:]
        final detailsLine = lines.indexOf('-- System Details --') + 1
        if (detailsLine > 0) {
            List<String> currentDetails = null
            String currentType = null
            for (int i = detailsLine + 1; i < lines.size(); i++) {
                final line = lines[i]

                final inf = line.indexOf(':')
                if (inf > 0 && !line.startsWith('\t\t')) {
                    final contents = line.substring(inf + 1).trim()
                    if (currentType !== null) {
                        details[currentType.trim()] = currentDetails
                        currentType = null
                        currentDetails = null
                    }
                    if (contents.blank) {
                        currentDetails = []
                        currentType = line.trim().dropRight(1)
                    } else {
                        details[line.substring(0, inf).trim()] = [contents]
                    }
                } else if (currentType !== null) {
                    currentDetails.add(line.trim())
                }
            }

            if (currentType !== null) {
                details[currentType.trim()] = currentDetails
            }
        }
        return details
    }()
}

@CompileStatic
final class LogException {
    int start, end
    List<String> stackTrace = []

    @PackageScope String theException

    @Lazy String[] exceptionStr = {
        final idx = theException.indexOf(':')
        final String[] ex = new String[2]
        ex[0] = theException.substring(0, idx)
        ex[1] = theException.substring(idx + 1).trim()
        return ex
    }()

    LogException causedBy
}

@CompileStatic
enum LogType {
    LOG,
    CRASH_LOG

    @Nullable
    @CompileStatic
    static LogType detectType(String str) {
        if (str.startsWith('---- Minecraft Crash Report ----')) {
            return CRASH_LOG
        } else if (str.contains('ModLauncher running: args')) {
            return LOG
        }
        return null
    }
}
