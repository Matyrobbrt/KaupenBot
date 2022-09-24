package com.matyrobbrt.kaupenbot.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.matyrobbrt.kaupenbot.commands.moderation.PermissionData;
import com.matyrobbrt.kaupenbot.commands.moderation.PermissionOwner;
import io.leangen.geantyref.TypeToken;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public interface LockdownsDAO extends Transactional<LockdownsDAO> {
    Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    Type TYPE = new TypeToken<Map<String, PermissionData>>() {}.getType();

    @Nullable
    default Map<PermissionOwner, PermissionData> get(long channelId) {
        return withHandle(handle -> handle.createQuery("select permissions from lockdowns where channelId = :ch")
                    .bind("ch", channelId)
                    .mapTo(String.class)
                    .findOne()
                    .<Map<String, PermissionData>>map(asString -> GSON.fromJson(asString, TYPE))
                    .map(it -> it.entrySet().stream().collect(Collectors.<Map.Entry<String, PermissionData>, PermissionOwner, PermissionData>toMap(
                            s -> {
                                final var split = s.getKey().split(":");
                                return new PermissionOwner(Long.parseLong(split[0]), PermissionOwner.PermissionType.valueOf(split[1]));
                            }, Map.Entry::getValue
                    )))
                    .orElse(null)
        );
    }

    @SqlUpdate("insert or replace into lockdowns(channelId, permissions) values (:ch, :perms)")
    void insert(@Bind("ch") long channelId, @Bind("perms") String data);

    default void insert(long channelId, Map<PermissionOwner, PermissionData> data) {
        final Map<String, PermissionData> map = new LinkedHashMap<>();
        data.forEach((key, val) -> map.put(key.owner + ":" + key.type.name(), val));
        insert(channelId, GSON.toJson(map));
    }

    @SqlUpdate("delete from lockdowns where channelId = :ch")
    void delete(@Bind("ch") long channelId);
}
