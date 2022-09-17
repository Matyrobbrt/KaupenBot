package com.matyrobbrt.kaupenbot.extensions

import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.MessageEmbed
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@CompileStatic
class StaticExtensions {
    static Jdbi load(Jdbi self, Path path, String dbName, String migrationName) {
        if (!Files.exists(path)) {
            Files.createFile(path)
        }
        final var url = "jdbc:sqlite:$path"
        final dataSource = new SQLiteDataSource()
        dataSource.setUrl(url)
        dataSource.setDatabaseName(dbName)

        final var flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/$migrationName")
                .load()
        flyway.migrate()

        return Jdbi.create(dataSource).installPlugin(new SqlObjectPlugin())
    }

    static MessageEmbed embed(Object self, @DelegatesTo(value = EmbedBuilder, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        final builder = new EmbedBuilder()
        closure.delegate = builder
        closure(builder)
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        return builder.build()
    }

    static Logger get(LoggerFactory self) {
        return LoggerFactory.getLogger(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass)
    }

    static String orNone(IMentionable self, List<? extends IMentionable> mentionables) {
        final String str = mentionables.stream().map(IMentionable::getAsMention).collect(Collectors.joining(' '))
        return str.isBlank() ? '_None_' : str
    }
}
