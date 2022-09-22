package com.matyrobbrt.kaupenbot.db

import com.matyrobbrt.kaupenbot.common.sql.SqlInsert
import com.matyrobbrt.kaupenbot.extensions.moderation.RolePanelsExtension.PanelMode
import com.matyrobbrt.kaupenbot.extensions.moderation.RolePanelsExtension.PanelType
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transactional

import java.sql.ResultSet
import java.sql.SQLException

@CompileStatic
@RegisterRowMapper(RolePanelInfo.Mapper)
interface RolePanelsDAO extends Transactional<RolePanelsDAO> {
    @SqlInsert(tableName = 'role_panels')
    void insert(String id, long channelId, long messageId, PanelMode mode, PanelType panelType)

    @SqlQuery('select * from role_panels where id = :id')
    RolePanelInfo get(@Bind('id') String id)

    @SqlQuery('select id from role_panels')
    List<String> getAllIds()

    @SqlUpdate('delete from role_panels where id = :id')
    void remove(@Bind('id') String id)
}

@CompileStatic
@TupleConstructor
class RolePanelInfo {
    String id
    long channelId, messageId
    PanelMode mode
    PanelType type

    @CompileStatic
    static final class Mapper implements RowMapper<RolePanelInfo> {

        @Override
        RolePanelInfo map(ResultSet rs, StatementContext ctx) throws SQLException {
            new RolePanelInfo(
                    rs.getString('id'),
                    rs.getLong('channelId'),
                    rs.getLong('messageId'),
                    PanelMode.valueOf(rs.getString('mode')),
                    PanelType.valueOf(rs.getString('panelType'))
            )
        }
    }
}