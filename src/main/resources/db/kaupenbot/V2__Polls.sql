create table polls
(
    channelId   big int not null,
    messageId   big int not null,
    ownerId     big int not null,
    expireOn    timestamp,
    finished    boolean not null,
    title       text    not null,
    pollOptions text    not null,
    constraint polls_pk primary key (channelId, messageId)
);