package com.matyrobbrt.kaupenbot.db

import com.matyrobbrt.kaupenbot.api.util.Warning
import groovy.transform.CompileStatic
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transactional

import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant

@CompileStatic
interface WarningsDAO extends Transactional<WarningsDAO> {

    /// Query methods ///

    @SqlQuery("select * from warnings where user_id = :user and guild_id = :guild")
    List<Warning> getWarningsForUser(@Bind("user") long user, @Bind("guild") long guild)

    @SqlQuery("select * from warnings")
    List<Warning> getAllWarnings()

    @SqlQuery("select reason from warnings where warn_id = :id")
    String getReason(@Bind("id") String warnId)

    @SqlQuery("select reason from warnings where warn_id = :id")
    Optional<String> getReasonOptional(@Bind("id") String warnId);

    @SqlQuery("select * from warnings where warn_id = :id")
    Warning getWarning(@Bind("id") String warnId)

    /// Deletion methods ///

    @SqlUpdate("delete from warnings where user_id = :user and guild_id = :guild")
    void clearAll(@Bind("user") long userId, @Bind("guild") long guildId)

    @SqlUpdate("delete from warnings where warn_id = :id")
    void deleteById(@Bind("id") String warnId)

}

@Newify(Warning)
@CompileStatic
final class WarningMapper implements RowMapper<Warning> {

    @Override
    Warning map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Warning(
                rs.getLong('user_id'),
                rs.getLong('guild_id'),
                rs.getLong('moderator'),
                UUID.fromString(rs.getString('warn_id')),
                rs.getString('reason'),
                rs.getTimestamp('timestamp').toInstant()
        )
    }

    static UUID insert(WarningsDAO self, long user, long guild, String reason, long moderator, Instant timestamp = Instant.now()) {
        final var id = UUID.randomUUID()
        self.getHandle().createUpdate('insert or ignore into warnings values (:user, :guild, :warn_id, :reason, :moderator, :timestamp)')
                .bind('user', user)
                .bind('guild', guild)
                .bind('warn_id', id)
                .bind('reason', reason)
                .bind('moderator', moderator)
                .bind('timestamp', timestamp)
                .execute()
        return id
    }
}