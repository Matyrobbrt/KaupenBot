package com.matyrobbrt.kaupenbot.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@RegisterRowMapper(YouTubeNotificationsDAO.Notification.Mapper.class)
public interface YouTubeNotificationsDAO {

    @SqlQuery("select * from youtube_notifications")
    List<Notification> getAll();

    @SqlQuery("select * from youtube_notifications where youtubeChannelId = :cid")
    List<Notification> getByYoutubeChannel(@Bind("cid") String youtubeChannelId);

    @Nullable
    @SqlQuery("select * from youtube_notifications where youtubeChannelId = :cid and discordChannelId = :discord")
    Notification get(@Bind("discord") long discordChannelId, @Bind("cid") String youtubeChannelId);

    @SqlUpdate("insert into youtube_notifications(discordChannelId, youtubeChannelId, pingRole) values (:discord, :youtube, :role)")
    void link(
            @Bind("discord") long discordChannelId,
            @Bind("youtube") String youtubeChannelId,
            @Bind("role") long pingRole
    );

    @SqlUpdate("delete from youtube_notifications where discordChannelId = :discord and youtubeChannelId = :youtube")
    void remove(
            @Bind("discord") long discordChannelId,
            @Bind("youtube") String youtubeChannelId
    );

    record Notification(long discordChannelId, String ytChannelId, long pingRole) {
        public static final class Mapper implements RowMapper<Notification> {
            @Override
            public Notification map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new Notification(
                        rs.getLong("discordChannelId"),
                        rs.getString("youtubeChannelId"),
                        rs.getLong("pingRole")
                );
            }
        }
    }
}
