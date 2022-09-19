package com.matyrobbrt.kaupenbot.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.leangen.geantyref.TypeToken;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface PollsDAO extends Transactional<PollsDAO> {

    Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    static String asString(List<Map.Entry<Emoji, String>> map) {
        final List<EmojiWithText> asList = new ArrayList<>();
        map.forEach(it -> asList.add(new EmojiWithText(it.getKey().getFormatted(), it.getValue())));
        return GSON.toJson(asList);
    }
    static List<Map.Entry<Emoji, String>> fromString(String value) {
        final var token = new TypeToken<List<EmojiWithText>>() {}.getType();
        final var list = GSON.<List<EmojiWithText>>fromJson(value, token);
        return list.stream().<Map.Entry<Emoji, String>>map(it -> new AbstractMap.SimpleEntry<>(Emoji.fromFormatted(it.emoji), it.text)).toList();
    }

    @SqlUpdate("insert into polls(channelId, messageId, ownerId, expireOn, finished, title, pollOptions, multipleChoices) values (:channel, :message, :owner, :expire, :finished, :title, :options, :multiOpt)")
    void add(
            @Bind("channel") long channelId,
            @Bind("message") long messageId,
            @Bind("owner") long ownerId,
            @Nullable @Bind("expire") Instant expireOn,
            @Bind("finished") boolean finished,
            @Bind("title") String title,
            @Bind("options") String pollOptions,
            @Bind("multiOpt") boolean multipleChoices
    );

    @SqlUpdate("update polls set finished = :finished where channelId = :channel and messageId = :message")
    void markFinished(
            @Bind("channel") long channelId,
            @Bind("message") long messageId,
            @Bind("finished") boolean finished
    );

    @UseRowMapper(SuggestionId.Mapper.class)
    @SqlQuery("select channelId, messageId from polls where expireOn <= :now and finished = :finished")
    List<SuggestionId> getExpiredSuggestions(@Bind("now") Instant timeNow, @Bind("finished") boolean finished);

    @SqlQuery("select pollOptions from polls where channelId = :channel and messageId = :message")
    String getOptions(
            @Bind("channel") long channelId,
            @Bind("message") long messageId
    );
    @SqlQuery("select ownerId from polls where channelId = :channel and messageId = :message")
    Long getOwner(
            @Bind("channel") long channelId,
            @Bind("message") long messageId
    );
    @SqlQuery("select finished from polls where channelId = :channel and messageId = :message")
    boolean isFinished(
            @Bind("channel") long channelId,
            @Bind("message") long messageId
    );
    @SqlQuery("select multipleChoices from polls where channelId = :channel and messageId = :message")
    boolean isMultipleChoices(
            @Bind("channel") long channelId,
            @Bind("message") long messageId
    );

    final class EmojiWithText {
        public String emoji, text;

        public EmojiWithText(String emoji, String text) {
            this.emoji = emoji;
            this.text = text;
        }
    }

    record SuggestionId(long channelId, long messageId) {
        public static final class Mapper implements RowMapper<SuggestionId> {

            @Override
            public SuggestionId map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new SuggestionId(rs.getLong("channelId"), rs.getLong("messageId"));
            }
        }
    }
}
