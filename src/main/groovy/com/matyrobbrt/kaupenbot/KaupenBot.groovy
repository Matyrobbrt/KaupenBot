package com.matyrobbrt.kaupenbot

import com.jagrosh.jdautilities.command.CommandClient
import com.jagrosh.jdautilities.command.CommandClientBuilder
import com.matyrobbrt.jdahelper.DismissListener
import com.matyrobbrt.jdahelper.components.ComponentListener
import com.matyrobbrt.jdahelper.components.storage.ComponentStorage
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.jdahelper.pagination.PaginatorBuilder
import com.matyrobbrt.kaupenbot.api.PluginRegistry
import com.matyrobbrt.kaupenbot.api.util.Warning
import com.matyrobbrt.kaupenbot.apiimpl.BasePluginRegistry
import com.matyrobbrt.kaupenbot.apiimpl.PluginLoader
import com.matyrobbrt.kaupenbot.apiimpl.plugins.CommandsPluginImpl
import com.matyrobbrt.kaupenbot.apiimpl.plugins.EventsPluginImpl
import com.matyrobbrt.kaupenbot.apiimpl.plugins.WarningPluginImpl
import com.matyrobbrt.kaupenbot.commands.EvalCommand
import com.matyrobbrt.kaupenbot.commands.context.GistContextMenu
import com.matyrobbrt.kaupenbot.commands.moderation.PurgeCommand
import com.matyrobbrt.kaupenbot.commands.moderation.WarnCommand
import com.matyrobbrt.kaupenbot.commands.moderation.WarningCommand
import com.matyrobbrt.kaupenbot.db.WarningMapper
import com.matyrobbrt.kaupenbot.listener.AutoGistDetection
import com.matyrobbrt.kaupenbot.listener.ThreadListeners
import com.matyrobbrt.kaupenbot.tricks.AddTrickCommand
import com.matyrobbrt.kaupenbot.tricks.RunTrickCommand
import com.matyrobbrt.kaupenbot.tricks.TrickCommand
import com.matyrobbrt.kaupenbot.tricks.Tricks
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
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
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

    static PluginRegistry plugins
    static Jdbi database
    static Config config
    static JDA jda
    static CommandClient client

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

        client = CommandClientBuilder().tap {
            ownerId = '0000000000'
            prefixes = config.prefixes
            activity = null

            addSlashCommands(WarningCommand(), PurgeCommand())
            addCommand(WarnCommand())
            EvalCommand().tap {
                addSlashCommand(it)
                addCommand(it)
            }
            // TODO slash command with modal for add trick
            addCommand(AddTrickCommand())
            addSlashCommand(TrickCommand())

            Tricks.getTricks().forEach { tr ->
                addCommand(new RunTrickCommand.Prefix(tr))
            }
        }.build()

        final List<EventListener> otherListeners = []
        otherListeners.add(new EvalCommand.ModalListener())
        if (env.get('GIST_TOKEN') !== null) {
            final gistToken = env.get('GIST_TOKEN')
            client.addContextMenu(new GistContextMenu(gistToken))
            otherListeners.add(new AutoGistDetection(gistToken))
        }

        jda = JDABuilder.createLight(token)
                .enableIntents(BotConstants.INTENTS)
                .addEventListeners(new DismissListener(), new ThreadListeners(), client, components)
                .addEventListeners(new ListenerAdapter() {
                    @Override
                    void onReady(@NotNull @Nonnull ReadyEvent event) {
                        log.warn('KaupenBot is ready to work. Logged in as: {} ({})', event.getJDA().selfUser.asTag, event.getJDA().selfUser.id)
                    }
                })
                .addEventListeners(otherListeners.toArray())
                .build()

        BotConstants.registerMappers(database)
        Constants.EXECUTOR.scheduleAtFixedRate({
            components.removeComponentsOlderThan(30, ChronoUnit.MINUTES)
        }, 0, 30, TimeUnit.MINUTES)

        BotConstants.preparePlugins()
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

    static void preparePlugins() {
        KaupenBot.plugins = new BasePluginRegistry()
        registerPlugins(KaupenBot.plugins)

        final scriptsDir = Path.of('scripts')
        final out = scriptsDir.resolve('.out').toAbsolutePath()
        final loader = new PluginLoader(
                ConfigurateUtils.CONFIG_WATCH_SERVICE,
                KaupenBot.plugins as BasePluginRegistry, new GroovyShell(new CompilerConfiguration().tap {
                    targetDirectory = out.toFile()

                    final imports = new ImportCustomizer()
                    imports.addStarImports('com.matyrobbrt.kaupenbot.api', 'com.matyrobbrt.kaupenbot.api.plugins')
                    imports.addImports('java.time.Duration')
                    addCompilationCustomizers(imports)
                })
        )
        loader.track(scriptsDir, out)
    }

    static void registerPlugins(PluginRegistry registry) {
        registry.registerPlugin('warnings', new WarningPluginImpl())

        final events = new EventsPluginImpl()
        KaupenBot.jda.addEventListener(events)
        registry.registerPlugin('events', events)
        registry.registerPlugin('commands', new CommandsPluginImpl(KaupenBot.client))
    }
}

@CompileStatic
@ConfigSerializable
class Config {
    long moderatorRole
    String[] prefixes = new String[] { '!', '-' }
    LoggingChannels loggingChannels = new LoggingChannels()

    @CompileStatic
    @ConfigSerializable
    static final class LoggingChannels {
        long moderationLogs
    }
}
