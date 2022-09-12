package com.matyrobbrt.kaupenbot

import com.jagrosh.jdautilities.command.CommandClientBuilder
import com.matyrobbrt.jdahelper.DismissListener
import com.matyrobbrt.jdahelper.components.ComponentListener
import com.matyrobbrt.jdahelper.components.storage.ComponentStorage
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.jdahelper.pagination.PaginatorBuilder
import com.matyrobbrt.kaupenbot.commands.WarnCommand
import com.matyrobbrt.kaupenbot.commands.WarningCommand
import com.matyrobbrt.kaupenbot.db.WarningMapper
import com.matyrobbrt.kaupenbot.db.Warning
import com.matyrobbrt.kaupenbot.listener.ThreadListeners
import com.matyrobbrt.kaupenbot.util.ConfigurateUtils
import com.matyrobbrt.kaupenbot.util.Constants
import com.matyrobbrt.kaupenbot.util.DeferredComponentListeners
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.stc.POJO
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.jdbi.v3.core.Jdbi
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.objectmapping.ConfigSerializable

import javax.annotation.Nonnull
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@CompileStatic
@Newify(pattern = '[A-z][A-Za-z0-9_]*')
final class KaupenBot {

    private static final DeferredComponentListeners COMPONENTS = DeferredComponentListeners()

    static ComponentListener.Builder getComponentListener(final String featureId) {
        COMPONENTS[featureId]
    }
    static PaginatorBuilder paginator(final String featureId) {
        Paginator.builder(getComponentListener(featureId))
    }

    public static final Logger log = LoggerFactory.get()

    static Jdbi database
    static Config config
    static JDA jda

    static void start(Dotenv env) throws IOException {
        final token = env.get('BOT_TOKEN')

        if (token === null) {
            log.warn('KaupenBot is disabled!')
            return
        }

        database = Jdbi.load(
                Path.of('data.db'),
                'KaupenBot Data',
                'kaupenbot'
        )

        final storage = ComponentStorage.sql(database, 'components')
        final components = COMPONENTS.createManager(storage)

        final configPath = Path.of('config.conf')
        config = ConfigurateUtils.loadConfig(
                HoconConfigurationLoader.builder()
                        .emitComments(true)
                        .path(configPath)
                        .build(),
                configPath, { config = it }, Config,
                new Config()
        ).value.get()

        final client = CommandClientBuilder().tap {
            ownerId = '0000000000'
            prefixes = config.prefixes
            activity = null

            addSlashCommand(WarningCommand())
            addCommand(WarnCommand())
        }.build()

        jda = JDABuilder.createLight(token)
                .enableIntents(BotConstants.INTENTS)
                .addEventListeners(new DismissListener(), new ThreadListeners(), client)
                .addEventListeners(new ListenerAdapter() {
                    @Override
                    void onReady(@NotNull @Nonnull ReadyEvent event) {
                        log.warn('KaupenBot is ready to work. Logged in as: {} ({})', event.getJDA().selfUser.asTag, event.getJDA().selfUser.id)
                    }
                })
                .build()

        BotConstants.registerMappers(database)
        Constants.EXECUTOR.scheduleAtFixedRate({
            components.removeComponentsOlderThan(30, ChronoUnit.MINUTES)
        }, 0, 30, TimeUnit.MINUTES)
    }
}

@POJO
@CompileStatic
@PackageScope(PackageScopeTarget.CLASS)
final class BotConstants {
    static final List<GatewayIntent> INTENTS = [
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_BANS
    ]

    @Newify(pattern = '[A-z][A-Za-z0-9_]*')
    static void registerMappers(Jdbi jdbi) {
        jdbi.registerRowMapper(Warning, WarningMapper())
    }
}

@CompileStatic
@ConfigSerializable
class Config {
    long moderatorRole
    String[] prefixes = new String[] { '!', '-' }
}
