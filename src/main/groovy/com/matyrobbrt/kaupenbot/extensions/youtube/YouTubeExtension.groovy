package com.matyrobbrt.kaupenbot.extensions.youtube

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.ExtensionArgument
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.db.YouTubeNotificationsDAO
import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.jdbi.v3.core.Jdbi

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@CompileStatic
@RegisterExtension(value = 'youtube', botId = 'kbot')
final class YouTubeExtension implements BotExtension {
    private final YoutubeWebhookManager manager

    YouTubeExtension(@ExtensionArgument('env') Dotenv env,
                     @ExtensionArgument('database') Jdbi db,
                     @ExtensionArgument('jda') JDA jda) {
        this.manager = new YoutubeWebhookManager(env.get('baseWebUrl'), jda, db)
    }

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'youtube-webhooks'
            description = 'YouTube upload webhooks stuff'
            require(Permission.MANAGE_CHANNEL)

            subCommand {
                name = 'link'
                description = 'Link this channel with a YouTube channel'
                options = [
                        new OptionData(OptionType.STRING, 'channel', 'The YouTube channel ID to link', true),
                        new OptionData(OptionType.ROLE, 'role', 'A role to ping when new videos are uploaded', true)
                ]
                action {
                    final roleId = it.role('role')?.idLong
                    final youtubeId = it.string('channel')

                    final notification = this.manager.database.withExtension(YouTubeNotificationsDAO) { db ->
                        db.get(it.channel.idLong, youtubeId)
                    }
                    if (notification !== null) {
                        it.replyProhibited('This channel is already linked with that YouTube channel!').queue()
                        return
                    }

                    this.manager.linkChannel(it.channel.idLong, youtubeId, roleId)
                    replyEphemeral("Linked channel with ID [${youtubeId}](https://www.youtube.com/channel/${youtubeId}) with channel ${it.channel.asMention}${roleId === 0 ? " and role <@&$roleId>" : ''}.")
                        .queue()
                }
            }

            subCommand {
                name = 'unlink'
                description = 'Unlink a YouTube channel from this channel.'
                options = [
                        new OptionData(OptionType.STRING, 'channel', 'The ID of the YouTube channel to unlink', true)
                ]

                action {
                    final youtubeId = it.string('channel')

                    final notification = this.manager.database.withExtension(YouTubeNotificationsDAO) { db ->
                        db.get(it.channel.idLong, youtubeId)
                    }
                    if (notification === null) {
                        it.replyProhibited('This channel is not linked with that YouTube channel!').queue()
                        return
                    }

                    this.manager.unlinkChannel(it.channel.idLong, youtubeId)
                    replyEphemeral('Unlinked YouTube channel!').queue()
                }
            }
        }
    }

    @Override
    void scheduleTasks(ScheduledExecutorService service) {
        service.scheduleAtFixedRate({
            manager.refreshSubscriptions()
        }, 0, 4, TimeUnit.DAYS) // Refresh every 4 days
    }

    @Override
    boolean usesWebServer() {
        return true
    }
    @Override
    void setupEndpoints(HttpServer server) {
        manager.registerEndpoints(server)
    }
}
