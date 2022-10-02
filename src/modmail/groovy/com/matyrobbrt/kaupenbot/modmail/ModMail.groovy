package com.matyrobbrt.kaupenbot.modmail

import com.jagrosh.jdautilities.command.CommandClientBuilder
import com.jagrosh.jdautilities.command.ContextMenu
import com.jagrosh.jdautilities.command.SlashCommand
import com.matyrobbrt.jdahelper.DismissListener
import com.matyrobbrt.jdahelper.components.ComponentListener
import com.matyrobbrt.jdahelper.components.storage.ComponentStorage
import com.matyrobbrt.jdahelper.pagination.Paginator
import com.matyrobbrt.jdahelper.pagination.PaginatorBuilder
import com.matyrobbrt.kaupenbot.common.command.CommandManagerImpl
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.ExtensionFinder
import com.matyrobbrt.kaupenbot.common.extension.ExtensionManager
import com.matyrobbrt.kaupenbot.common.util.ConfigurateUtils
import com.matyrobbrt.kaupenbot.common.util.Constants
import com.matyrobbrt.kaupenbot.common.util.DeferredComponentListeners
import com.matyrobbrt.kaupenbot.common.util.EventManagerWithFeedback
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.localization.ResourceBundleLocalizationFunction
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jdbi.v3.core.Jdbi
import org.jetbrains.annotations.NotNull
import org.spongepowered.configurate.hocon.HoconConfigurationLoader

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Slf4j
@CompileStatic
@Newify(pattern = '[A-z][A-Za-z0-9_]*')
final class ModMail {
    private static final DeferredComponentListeners COMPONENTS = new DeferredComponentListeners()

    static ComponentListener.Builder getComponentListener(final String featureId) {
        COMPONENTS[featureId]
    }
    static PaginatorBuilder paginator(final String featureId) {
        Paginator.builder(getComponentListener(featureId))
    }

    static Jdbi database
    static JDA jda
    static ModMailConfig config

    @SuppressWarnings('unused')
    static void start(Path dir, Dotenv dotenv) throws IOException {
        database = Jdbi.load(
                dir.resolve('data.db'),
                'ModMail Data',
                'modmail'
        )

        final storage = ComponentStorage.sql(database, 'components')
        final components = COMPONENTS.createManager(storage)

        final configPath = dir.resolve('config.json')
        config = ConfigurateUtils.loadConfig(
                HoconConfigurationLoader.builder()
                        .emitJsonCompatible(true)
                        .path(configPath)
                        .build(),
                configPath, { config = it }, ModMailConfig,
                new ModMailConfig()
        ).value.get()

        final client = CommandClientBuilder().tap {
            ownerId = '0000000000'
            prefixes = config.prefixes
            activity = null
            manualUpsert = true
            forceGuildOnly(config.guildId)
        }.build()

        final commands = new CommandManagerImpl(ResourceBundleLocalizationFunction.empty().build())

        final extensions = new ExtensionManager(null)
        findExtensions(extensions, [:])
        extensions.forEachEnabled {
            it.registerCommands(commands, client)
        }

        jda = JDABuilder.createLight(dotenv.get('MOD_MAIL_TOKEN'))
            .setEventManager(new EventManagerWithFeedback())
            .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
            .setActivity(Activity.watching('for your DMs'))
            .addEventListeners(client, commands, new DismissListener(), components)
            .addEventListeners(new ListenerAdapter() {
                @Override
                void onReady(@NotNull @Nonnull ReadyEvent event) {
                    log.warn('ModMail is ready to work. Logged in as: {} ({})', event.getJDA().selfUser.asTag, event.getJDA().selfUser.id)

                    List<CommandData> data = []
                    for (SlashCommand command : client.slashCommands) {
                        data.add(command.buildCommandData())
                    }
                    for (ContextMenu menu : client.contextMenus) {
                        data.add(menu.buildCommandData())
                    }
                    commands.upsert(event.getJDA(), data)
                }
            })
            .setStatus(OnlineStatus.IDLE)
            .build()

        extensions.forEachEnabled {
            it.subscribeEvents(jda)
        }

        Constants.EXECUTOR.scheduleAtFixedRate({
            components.removeComponentsOlderThan(30, ChronoUnit.MINUTES)
        }, 0, 30, TimeUnit.MINUTES)
    }

    @ExtensionFinder('modmail')
    private static void findExtensions(ExtensionManager extensions, Map args) {
        throw new UnsupportedOperationException()
    }

    static Guild getGuild() {
        return jda.getGuildById(config.guildId)
    }

    @Nullable
    static Long activeTicket(long userId) {
        return database.withExtension(TicketsDAO) {
            it.getThreads(userId, true)
        }.find()
    }

    static RestAction<Message> log(MessageCreateData data) {
        return loggingChannel.sendMessage(data)
    }

    static MessageChannel getLoggingChannel() {
        guild.getChannelById(MessageChannel, config.loggingChannel)
    }
}