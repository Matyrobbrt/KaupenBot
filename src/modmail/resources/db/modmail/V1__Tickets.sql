create table tickets
(
    userId   unsigned big int not null,
    threadId unsigned big int not null primary key,
    active   boolean not null
);
create table ticket_messages
(
    webhookMessage unsigned big int not null primary key,
    dmMessage      unsigned big int not null
)