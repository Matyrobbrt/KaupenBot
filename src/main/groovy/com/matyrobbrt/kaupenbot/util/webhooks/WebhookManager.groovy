package com.matyrobbrt.kaupenbot.util.webhooks

import club.minnced.discord.webhook.external.JDAWebhookClient
import club.minnced.discord.webhook.send.AllowedMentions
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import net.dv8tion.jda.api.entities.IWebhookContainer
import net.dv8tion.jda.api.entities.ThreadChannel
import net.dv8tion.jda.api.entities.Webhook
import okhttp3.OkHttpClient
import org.jetbrains.annotations.Nullable

import java.util.concurrent.*
import java.util.function.Consumer
import java.util.function.Predicate

@CompileStatic
abstract class WebhookManager {
    static WebhookManager of(String name) {
        return of(e -> e.trim() == name, name, AllowedMentions.none())
    }

    static WebhookManager of(Predicate<String> matcher, String webhookName, AllowedMentions allowedMentions, @Nullable Consumer<Webhook> creationListener) {
        return new WebhookManagerImpl(matcher, webhookName, allowedMentions, creationListener)
    }

    static WebhookManager of(Predicate<String> matcher, String webhookName, AllowedMentions allowedMentions) {
        return of(matcher, webhookName, allowedMentions, null)
    }

    /**
     * Gets or creates the webhook client for a given {@code channel}.
     *
     * @param channel the channel to get or create the webhook in
     * @return the webhook
     */
    abstract JDAWebhookClient getWebhook(IWebhookContainer channel)
    /**
     * Gets or creates the webhook client for a given {@code thread}.
     *
     * @param thread the thread to get or create the webhook in
     * @return the webhook
     */
    abstract JDAWebhookClient getWebhook(ThreadChannel thread)

    JDAWebhookClient getAt(IWebhookContainer channel) {
        getWebhook(channel)
    }
    JDAWebhookClient getAt(ThreadChannel thread) {
        getWebhook(thread)
    }
}

@CompileStatic
@PackageScope(PackageScopeTarget.CLASS)
final class WebhookManagerImpl extends WebhookManager {
    private static final List<WebhookManagerImpl> MANAGERS = new CopyOnWriteArrayList<>()
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient()
    private static ScheduledExecutorService executor

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                MANAGERS.forEach(WebhookManagerImpl::close), "WebhookClosing"))
    }

    private static ScheduledExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newScheduledThreadPool(Math.max(MANAGERS.size() / 3 as int, 1) as int, r -> new Thread(r, "Webhooks").tap { it.daemon = true })
            // Clear webhooks after 6 hours to refresh them
            getExecutor().scheduleAtFixedRate(() -> MANAGERS.forEach(it -> it.webhooks.clear()), 1, 6, TimeUnit.HOURS)
        }
        return executor
    }

    private final Predicate<String> predicate
    private final String webhookName
    private final AllowedMentions allowedMentions
    private final Map<Long, JDAWebhookClient> webhooks = new ConcurrentHashMap<>()
    @Nullable
    private final Consumer<Webhook> creationListener

    WebhookManagerImpl(final Predicate<String> predicate, final String webhookName, final AllowedMentions allowedMentions, @javax.annotation.Nullable final Consumer<Webhook> creationListener) {
        this.predicate = predicate
        this.webhookName = webhookName
        this.allowedMentions = allowedMentions
        this.creationListener = creationListener
        MANAGERS.add(this)
    }

    @Override
    JDAWebhookClient getWebhook(final IWebhookContainer channel) {
        webhooks.computeIfAbsent(channel.idLong) {
            create(getOrCreateWebhook(channel))
        }
    }

    @Override
    JDAWebhookClient getWebhook(ThreadChannel thread) {
        webhooks.computeIfAbsent(thread.idLong) {
            create(getOrCreateWebhook(thread.parentMessageChannel.asTextChannel())).onThread(thread.idLong)
        }
    }

    private JDAWebhookClient create(Webhook webhook) {
        WebhookMessageSender.create(
                webhook, getExecutor(), HTTP_CLIENT, allowedMentions
        )
    }

    private Webhook getOrCreateWebhook(IWebhookContainer channel) {
        Objects.requireNonNull(channel).retrieveWebhooks()
                .submit(false).get()
                .find { predicate.test(it.name) }
        ?: {
            final var webhook = channel.createWebhook(webhookName).submit(false).get()
            if (creationListener != null) {
                creationListener.accept(webhook)
            }
            return webhook
        }.call()
    }

    void close() {
        webhooks.forEach((id, client) -> client.close())
    }
}
