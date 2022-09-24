package com.matyrobbrt.kaupenbot.db;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.List;

public interface StickyRolesDAO extends Transactional<StickyRolesDAO> {
    @SqlUpdate("insert into sticky_roles values (:user, :role, :guild)")
    void insert(@Bind("user") long userId, @Bind("role") long roleId, @Bind("guild") long guildId);

    default void insert(long userId, long guildId, Iterable<Long> roles) {
        roles.forEach(roleId -> insert(userId, roleId, guildId));
    }

    @SqlQuery("select role_id from sticky_roles where user_id = :user and guild_id = :guild")
    List<Long> getRoles(@Bind("user") long userId, @Bind("guild") long guildId);

    @SqlUpdate("delete from sticky_roles where user_id = :user and role_id = :role and guild_id = :guild")
    void delete(@Bind("user") long userId, @Bind("role") long roleId, @Bind("guild") long guildId);

    @SqlUpdate("delete from sticky_roles where user_id = :user and guild_id = :guild")
    void clear(@Bind("user") long userId, @Bind("guild") long guildId);
}
