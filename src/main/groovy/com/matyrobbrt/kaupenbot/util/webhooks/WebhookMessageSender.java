package com.matyrobbrt.kaupenbot.util.webhooks;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import okhttp3.OkHttpClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

public class WebhookMessageSender {
    public static CompletableFuture<ReadonlyMessage> send(WebhookClient client, MessageEmbed embed, String username, String avatar) {
        return client.send(new WebhookMessageBuilder()
                .addEmbeds(WebhookEmbedBuilder.fromJDA(embed).build())
                .setAvatarUrl(avatar)
                .setUsername(username)
                .build());
    }
    public static JDAWebhookClient create(Webhook webhook, ScheduledExecutorService executor, OkHttpClient httpClient, AllowedMentions allowedMentions) {
        return WebhookClientBuilder.fromJDA(webhook)
                .setExecutorService(executor)
                .setHttpClient(httpClient)
                .setAllowedMentions(allowedMentions)
                .buildJDA();
    }
}
