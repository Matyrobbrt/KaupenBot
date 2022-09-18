package com.matyrobbrt.kaupenbot.modmail.db

import groovy.transform.CompileStatic
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transactional

import javax.annotation.Nullable

@CompileStatic
interface TicketsDAO extends Transactional<TicketsDAO> {
    @SqlQuery('select threadId from tickets where userId = :user and active = :active')
    List<Long> getThreads(@Bind('user') long userId, @Bind('active') boolean active)
    @SqlQuery('select threadId from tickets where userId = :user')
    List<Long> getThreads(@Bind('user') long userId)

    @SqlUpdate('insert into tickets values(:user, :thread, :active)')
    void insertThread(@Bind('user') long user, @Bind('thread') long thread, @Bind('active') boolean active)

    @Nullable
    @SqlQuery('select userId from tickets where threadId = :thread and active = :active')
    Long getUser(@Bind('thread') long threadId, @Bind('active') boolean active)

    @SqlUpdate('delete from tickets where threadId = :thread')
    void removeThread(@Bind('thread') long threadId)

    @SqlUpdate('update tickets set active = :active where threadId = :thread')
    void markActive(@Bind('thread') long threadId, @Bind('active') boolean active)

    @SqlUpdate('insert into ticket_messages values(:webhook, :dm)')
    void insertMessageAssociation(@Bind('webhook') long webhookMessageId, @Bind('dm') long dmMessageId)

    @Nullable
    @SqlQuery('select dmMessage from ticket_messages where webhookMessage = :webhook')
    Long getAssociatedMessage(@Bind('webhook') long webhookMessageId)
}