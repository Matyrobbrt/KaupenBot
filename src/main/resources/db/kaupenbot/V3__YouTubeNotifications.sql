create table youtube_notifications
(
    discordChannelId big int not null,
    youtubeChannelId text not null,
    pingRole         big int,
    constraint pk_yt_notifications primary key (discordChannelId, youtubeChannelId)
);