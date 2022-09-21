package com.matyrobbrt.kaupenbot.common.util.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

public class MessageCacheBuilder {

    private Caffeine<Object, Object> messageCache = Caffeine.newBuilder()
        .maximumSize(100_100)
        .expireAfterWrite(Duration.of(2, ChronoUnit.HOURS));
    private BiConsumer<MessageUpdateEvent, MessageData> onEdit;
    private BiConsumer<MessageDeleteEvent, MessageData> onDelete;

    public MessageCacheBuilder onEdit(final BiConsumer<MessageUpdateEvent, MessageData> onEdit) {
        this.onEdit = onEdit;
        return this;
    }

    public MessageCacheBuilder onDelete(final BiConsumer<MessageDeleteEvent, MessageData> onDelete) {
        this.onDelete = onDelete;
        return this;
    }

    @SuppressWarnings("unused")
    public MessageCacheBuilder caffeine(@NotNull final UnaryOperator<Caffeine<Object, Object>> operator) {
        this.messageCache = operator.apply(messageCache);
        return this;
    }

    public JdaMessageCache build() {
        return new JdaMessageCacheImpl(messageCache.build(), onEdit, onDelete);
    }
}
