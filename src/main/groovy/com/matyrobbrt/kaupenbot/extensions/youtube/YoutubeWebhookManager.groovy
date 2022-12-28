package com.matyrobbrt.kaupenbot.extensions.youtube

import club.minnced.discord.webhook.send.AllowedMentions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookManager
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookManagerImpl
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender
import com.matyrobbrt.kaupenbot.db.YouTubeNotificationsDAO
import com.matyrobbrt.kaupenbot.db.YouTubeNotificationsDAO.Notification
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.xml.XmlParser
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jdbi.v3.core.Jdbi
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

@CompileStatic
final class YoutubeWebhookManager {
    static final Logger LOG = LoggerFactory.get()
    private static final String URL_TEMPLATE = 'https://www.youtube.com/xml/feeds/videos.xml?channel_id='
    private static final String VIDEO_URL_TEMPLATE = 'https://www.youtube.com/watch?v='

    private final String baseUrl
    private final JDA jda
    final Jdbi database
    private final WebhookManager webhooks
    private final OkHttpClient client = WebhookManagerImpl.HTTP_CLIENT

    YoutubeWebhookManager(String baseUrl, JDA jda, Jdbi database) {
        this.baseUrl = baseUrl
        this.jda = jda
        this.database = database

        this.webhooks = WebhookManager.of('YouTubeNotifications')
    }

    @PackageScope registerEndpoints(HttpServer server) {
        server.createContext('/youtube') {
            final params = queryToMap(it.requestURI.query)
            if (params.containsKey('hub.challenge')) {
                final isSubscribe = params['hub.mode'] == 'subscribe'
                final channelId = params['hub.topic'].replace(URL_TEMPLATE, '')
                final noLinkedChannel = database.withExtension(YouTubeNotificationsDAO) { it.getByYoutubeChannel(channelId) }.isEmpty()
                if (isSubscribe === noLinkedChannel) {
                    it.reply('Invalid request!', HttpURLConnection.HTTP_FORBIDDEN)
                } else {
                    it.reply(params['hub.challenge'], HttpURLConnection.HTTP_OK)
                }
                return
            }

            try {
                handleVideo(it)
            } catch (Exception ex) {
                LOG.error('Encountered exception trying to handle YouTube notification feed!', ex)
                it.reply('Encountered exception processing request!', HttpURLConnection.HTTP_INTERNAL_ERROR)
            }
        }
    }

    static Map<String, String> queryToMap(String query) {
        if (query === null) {
            return new HashMap<>()
        }
        Map<String, String> result = new HashMap<>()
        for (String param : query.split('&')) {
            String[] entry = param.split('=')
            if (entry.length > 1) {
                result.put(entry[0], URLDecoder.decode(entry.drop(1).join('='), 'utf-8'))
            } else {
                result.put(entry[0], '')
            }
        }
        return result
    }

    void handleVideo(HttpExchange it) {
        try (final input = it.requestBody) {
            final data = new String(input.readAllBytes())
            if (data.isEmpty()) return

            final xml = new XmlParser().parseText(data)
            final entryNode = xml.getFirst('entry')

            final videoId = entryNode?.getFirst('yt:videoId')?.text()
            if (videoId === null) return
            final channelId = entryNode.getFirst('yt:channelId').text()

            final channels = database.withExtension(YouTubeNotificationsDAO) { it.getByYoutubeChannel(channelId) }
            if (channels.isEmpty()) {
                requestSubscription(channelId, 'unsubscribe')
            }

            final timestamp = OffsetDateTime.parse(entryNode.getFirst('published').text())
            if (timestamp.isBefore(OffsetDateTime.now().minusWeeks(3))) {
                // Video is older than 3 weeks, definitely don't send notifications
                return
            }

            if (VideoManager.KNOWN_VIDEOS.contains(videoId)) return
            VideoManager.addVideo(videoId)

            final channelName = entryNode.getFirst('author')?.getFirst('name')?.text()
            channels.each { notification -> sendNotification(notification, videoId, channelName) }

            it.reply('Notifications sent!', HttpURLConnection.HTTP_OK)
        }
    }

    void sendNotification(Notification notification, String videoId, @Nullable String channelName) {
        String message = notification.pingRole() === 0L ? '' : "<@&${notification.pingRole()}> "
        if (channelName !== null && !channelName.isEmpty()) {
            message += "**${channelName}** uploaded a new YouTube video!"
        } else {
            message += 'A new YouTube video has been uploaded!'
        }
        message += "\n${VIDEO_URL_TEMPLATE + videoId}"
        final channel = jda.getChannelById(MessageChannel, notification.discordChannelId())
        if (channel === null) {
            // Channel is null, remove the notification altogether
            database.useExtension(YouTubeNotificationsDAO) {
                it.remove(notification.discordChannelId(), notification.ytChannelId())
            }
            requestSubscription(notification.ytChannelId(), 'unsubscribe')
            return
        }
        if (channel instanceof IWebhookContainer) {
            WebhookMessageSender.send(webhooks[channel as IWebhookContainer], message, new AllowedMentions().withRoles(
                    String.valueOf(notification.pingRole())
            ))
        } else {
            channel.sendMessage(message).setAllowedMentions([Message.MentionType.USER]).queue()
        }
    }

    void refreshSubscriptions() {
        database.useExtension(YouTubeNotificationsDAO) {
            it.all.each {
                requestSubscription(it.ytChannelId())
            }
        }
    }

    void linkChannel(long discordChannelId, String youtubeChannelId, long role) {
        database.useExtension(YouTubeNotificationsDAO) {
            it.link(discordChannelId, youtubeChannelId, role)
        }
        requestSubscription(youtubeChannelId)
    }

    void unlinkChannel(long discordChannelId, String youtubeChannelId) {
        database.useExtension(YouTubeNotificationsDAO) {
            it.remove(discordChannelId, youtubeChannelId)
        }
        requestSubscription(youtubeChannelId, 'unsubscribe')
    }

    private void requestSubscription(String channelId, String mode = 'subscribe') {
        final params = [
                'hub.callback': "$baseUrl/youtube",
                'hub.topic'   : "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
                'hub.mode'    : mode,
                'hub.verify'  : 'sync'
        ]
        client.newCall(new Request.Builder()
                .method('POST', RequestBody.create(new byte[0]))
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .url('https://pubsubhubbub.appspot.com/subscribe?' +
                        params.entrySet().stream().map {
                            it.key + '=' + URLEncoder.encode(it.value.toString(), Charset.forName('utf-8'))
                        }.toList().join('&'))
                .build())
                .enqueue(new Callback() {
                    @Override
                    void onFailure(@NotNull Call call, @NotNull IOException e) {

                    }

                    @Override
                    void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        response.close()
                    }
                })
    }
}

@CompileStatic
final class VideoManager {
    private static final Gson GSON = new Gson()
    private static final Path PATH = Path.of('youtube_known_videos.json')
    private static Set<String> KNOWN_VIDEOS

    static Set<String> getKNOWN_VIDEOS() {
        if (KNOWN_VIDEOS != null) {
            return KNOWN_VIDEOS
        }

        if (!Files.exists(PATH)) {
            return KNOWN_VIDEOS = Collections.synchronizedSet(new HashSet<>())
        }
        try {
            try (final reader = PATH.newReader()) {
                return KNOWN_VIDEOS = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType())
            }
        } catch (final IOException exception) {
            YoutubeWebhookManager.LOG.error('Failed to read quote file: ', exception)
            return KNOWN_VIDEOS = Collections.synchronizedSet(new HashSet<>())
        }
    }

    static void addVideo(String knownVideo) {
        KNOWN_VIDEOS.add(knownVideo)
        syncVideos()
    }

    static void syncVideos() {
        try (final writer = PATH.newWriter(false)) {
            GSON.toJson(KNOWN_VIDEOS, writer)
        }
    }
}