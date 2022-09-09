package com.matyrobbrt.kaupenbot.modmail

import com.jagrosh.jdautilities.command.CommandClientBuilder
import com.matyrobbrt.kaupenbot.modmail.commands.ACloseCommand
import com.matyrobbrt.kaupenbot.modmail.commands.AReplyCommand
import com.matyrobbrt.kaupenbot.modmail.commands.CloseCommand
import com.matyrobbrt.kaupenbot.modmail.commands.ReplyCommand
import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import com.matyrobbrt.kaupenbot.util.ConfigurateUtils
import groovy.transform.CompileStatic
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jdbi.v3.core.Jdbi
import org.spongepowered.configurate.hocon.HoconConfigurationLoader

import javax.annotation.Nullable
import java.nio.file.Path

@CompileStatic
final class ModMail {
    static Jdbi database
    static JDA jda
    static ModMailConfig config
    static void start(Path dir, Dotenv dotenv) throws IOException {
        database = Jdbi.load(
                dir.resolve('data.db'),
                'ModMail Data',
                'modmail'
        )

        final configPath = dir.resolve('config.json')
        config = ConfigurateUtils.loadConfig(
                HoconConfigurationLoader.builder()
                        .emitJsonCompatible(true)
                        .path(configPath)
                        .build(),
                configPath, { config = it }, ModMailConfig,
                new ModMailConfig()
        ).value.get()

        final commands = [
                new AReplyCommand(), new ReplyCommand(),
                new CloseCommand(), new ACloseCommand()
        ]

        final client = new CommandClientBuilder().tap {
            ownerId = '0000000000'
            prefixes = config.prefixes
            activity = null
            commands.each {
                addCommand(it)
                addSlashCommand(it)
            }
            forceGuildOnly(config.guildId)
        }.build()

        jda = JDABuilder.createLight(dotenv.get('MOD_MAIL_TOKEN'))
            .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
            .setActivity(Activity.watching('for your DMs'))
            .addEventListeners(client, new ModMailListener())
            .build()
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