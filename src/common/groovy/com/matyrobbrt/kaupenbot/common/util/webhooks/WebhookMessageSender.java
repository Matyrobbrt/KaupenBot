package com.matyrobbrt.kaupenbot.common.util.webhooks;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

public class WebhookMessageSender {
    public static void sendMessage(String url, String message) {
        try (final var hook = new WebhookClientBuilder(url).build()) {
            hook.send(message);
        }
    }
    public static void sendMessage(String url, String message, AllowedMentions allowedMentions) {
        try (final var hook = new WebhookClientBuilder(url).build()) {
            hook.send(new WebhookMessageBuilder()
                    .setContent(message)
                    .setAllowedMentions(allowedMentions)
                    .build());
        }
    }

    public static CompletableFuture<ReadonlyMessage> send(WebhookClient client, String message, AllowedMentions allowedMentions) {
        return client.send(new WebhookMessageBuilder()
                .setContent(message)
                .setAllowedMentions(allowedMentions)
                .build());
    }

    public static CompletableFuture<ReadonlyMessage> send(WebhookClient client, @Nullable MessageEmbed embed, String username, String avatar, Attachment... attachments) {
        final var message = new WebhookMessageBuilder()
                .setAvatarUrl(avatar)
                .setUsername(username);
        if (embed != null) {
            message.addEmbeds(WebhookEmbedBuilder.fromJDA(embed).build());
        }
        for (Attachment attachment : attachments) {
            message.addFile(attachment.name(), attachment.data());
        }
        return client.send(message.build());
    }
    public static JDAWebhookClient create(Webhook webhook, ScheduledExecutorService executor, OkHttpClient httpClient, AllowedMentions allowedMentions) {
        return WebhookClientBuilder.fromJDA(webhook)
                .setExecutorService(executor)
                .setHttpClient(httpClient)
                .setAllowedMentions(allowedMentions)
                .buildJDA();
    }

    public record Attachment(String name, byte[] data) {
        public static Attachment[] from(List<Message.Attachment> attachments) {
            return attachments.stream().map(it -> {
                try (final var is = it.getProxy().download().get()) {
                    return new Attachment(it.getFileName(), is.readAllBytes());
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toArray(Attachment[]::new);
        }
    }
}
