create table role_panels
(
    id        text not null primary key,
    channelId big int not null,
    messageId big int not null,
    mode      int not null,
    panelType int not null
);