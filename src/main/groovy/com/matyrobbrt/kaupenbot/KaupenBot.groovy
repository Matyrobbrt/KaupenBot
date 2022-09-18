package com.matyrobbrt.kaupenbot

import com.jagrosh.jdautilities.command.CommandClient
import com.jagrosh.jdautilities.command.CommandClientBuilder
import com.jagrosh.jdautilities.command.ContextMenu
import com.jagrosh.jdautilities.command.SlashCommand
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
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.command.CommandManagerImpl
import com.matyrobbrt.kaupenbot.common.extension.ExtensionFinder
import com.matyrobbrt.kaupenbot.commands.context.AddQuoteContextMenu
import com.matyrobbrt.kaupenbot.commands.moderation.WarnCommand
import com.matyrobbrt.kaupenbot.commands.moderation.WarningCommand
import com.matyrobbrt.kaupenbot.db.WarningMapper
import com.matyrobbrt.kaupenbot.listener.ThreadListeners
import com.matyrobbrt.kaupenbot.quote.QuoteCommand
import com.matyrobbrt.kaupenbot.tricks.AddTrickCommand
import com.matyrobbrt.kaupenbot.tricks.RunTrickCommand
import com.matyrobbrt.kaupenbot.tricks.TrickCommand
import com.matyrobbrt.kaupenbot.tricks.Tricks
import com.matyrobbrt.kaupenbot.common.util.ConfigurateUtils
import com.matyrobbrt.kaupenbot.common.util.Constants
import com.matyrobbrt.kaupenbot.common.util.DeferredComponentListeners
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.stc.POJO
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.localization.ResourceBundleLocalizationFunction
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
    static final Map<DiscordLocale, ResourceBundle> BUNDLES = new HashMap<>()

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
            manualUpsert = true

            addSlashCommands(WarningCommand(), QuoteCommand())
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

            addContextMenus(new AddQuoteContextMenu())
        }.build()

        final bundleName = 'localization/commands'
        final locales = [] as List<DiscordLocale>
        locales.each {
            BUNDLES[it] = ResourceBundle.getBundle(bundleName, Locale.forLanguageTag(it.locale))
        }
        final localization = ResourceBundleLocalizationFunction.fromBundles(bundleName, locales.toArray(DiscordLocale[]::new)).build()
        final commands = new CommandManagerImpl(localization)

        final extensions = findExtensions([
                'env' : env
        ])
        extensions.each {
            it.registerCommands(commands, client)
        }

        jda = JDABuilder.createLight(token)
                .enableIntents(BotConstants.INTENTS)
                .addEventListeners(new DismissListener(), new ThreadListeners(), commands, client, components)
                .addEventListeners(new ListenerAdapter() {
                    @Override
                    void onReady(@NotNull @Nonnull ReadyEvent event) {
                        log.warn('KaupenBot is ready to work. Logged in as: {} ({})', event.getJDA().selfUser.asTag, event.getJDA().selfUser.id)

                        List<CommandData> data = new ArrayList<>()

                        // Build the commands
                        for (SlashCommand command : client.slashCommands) {
                            data.add(command.buildCommandData().setLocalizationFunction(localization))
                        }

                        for (ContextMenu menu : client.contextMenus) {
                            data.add(menu.buildCommandData().setLocalizationFunction(localization))
                        }

                        commands.upsert(event.getJDA(), data)
                    }
                })
                .addEventListeners(new EvalCommand.ModalListener())
                .build()
        extensions.each { it.subscribeEvents(jda) }

        BotConstants.registerMappers(database)
        Constants.EXECUTOR.scheduleAtFixedRate({
            components.removeComponentsOlderThan(30, ChronoUnit.MINUTES)
        }, 0, 30, TimeUnit.MINUTES)

        BotConstants.preparePlugins(extensions)
    }

    @ExtensionFinder('kbot')
    private static List<BotExtension> findExtensions(Map args) {
        throw new UnsupportedOperationException()
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

    static void preparePlugins(List<BotExtension> extensions) {
        KaupenBot.plugins = new BasePluginRegistry()
        registerPlugins(KaupenBot.plugins)

        for (final ext : extensions) ext.registerPlugins(KaupenBot.plugins)

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
    long joinRole
    String[] prefixes = new String[] { '!', '-' }
    LoggingChannels loggingChannels = new LoggingChannels()
    Channels channels = new Channels()

    @CompileStatic
    @ConfigSerializable
    static final class LoggingChannels {
        long moderationLogs
        long leaveJoinLogs
    }
    @CompileStatic
    @ConfigSerializable
    static final class Channels {
        List<Long> suggestionChannels = []
    }
}
