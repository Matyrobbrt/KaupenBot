package com.matyrobbrt.kaupenbot.script;

import groovy.lang.Closure;
import groovy.transform.CompileStatic;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;

@CompileStatic
public class ScriptObjects {
    public static final EnumSet<Message.MentionType> ALLOWED_MENTIONS = EnumSet.of(Message.MentionType.EMOJI,
            Message.MentionType.CHANNEL);

    public static ScriptArgument snowflake(ISnowflake snowflake) {
        final var ctx = ScriptArgument.make()
                .addProperty("id", snowflake.getId())
                .addProperty("timeCreated", snowflake.getTimeCreated());
        if (snowflake instanceof IMentionable mention) {
            ctx.addProperty("asMention", mention.getAsMention());
        }
        return ctx;
    }

    public static ScriptArgument channel(Channel channel) {
        return snowflake(channel)
                .addProperty("name", channel.getName())
                .addProperty("type", channel.getType());
    }

    public static ScriptArgument messageChannel(MessageChannel channel, boolean canSendMessage) {
        final var context = channel(channel);
        context.addMethod("canBotTalk", channel::canTalk);
        if (canSendMessage) {
            context.addVoidMethod("sendMessage", 1, args -> channel.sendMessage(message(args, 0)).setAllowedMentions(ALLOWED_MENTIONS).queue());
            context.addVoidMethod("sendEmbed", 1, args -> {
                final var embed = embed(args, 0);
                if (embed != null) {
                    channel.sendMessageEmbeds(embed).setAllowedMentions(ALLOWED_MENTIONS).queue();
                }
            });
            context.addVoidMethod("sendEmbeds", -1, args -> channel.sendMessageEmbeds(args.stream(i -> embed(args, i))
                    .filter(Objects::nonNull).limit(3).toList()).setAllowedMentions(ALLOWED_MENTIONS).queue());
        }
        return context;
    }

    public static ScriptArgument textChannel(TextChannel channel, boolean canSendMessage) {
        return messageChannel(channel, canSendMessage)
                .set("slowmode", channel.getSlowmode())
                .set("topic", channel.getTopic())
                .set("isNSFW", channel.isNSFW())
                .set("isSynced", channel.isSynced());
    }

    public static ScriptArgument user(User user, boolean canDm) {
        return snowflake(user)
            .set("name", user.getName())
            .set("discriminator", user.getDiscriminator())
            .set("avatarId", user.getAvatarId())
            .set("avatarUrl", user.getAvatarUrl())
            .set("isBot", user.isBot())
            .set("hasPrivateChannel", user.hasPrivateChannel())
            .addMethod("getAsTag", user::getAsTag)
            .addMethod("openPrivateChannel", 0, args -> {
                final var privateChannel = user.openPrivateChannel().complete();
                return privateChannel == null ? null : messageChannel(privateChannel, canDm);
            });
    }

    public static ScriptArgument member(Member member, boolean canDm) {
        return snowflake(member)
            .addCachedProperty("user", () -> user(member.getUser(), canDm))
            .set("nickname", member.getNickname())
            .set("color", member.getColorRaw())
            .set("timeBoosted", member.getTimeBoosted())
            .set("joinTime", member.getTimeJoined())
            .addMethod("getStatus", member::getOnlineStatus)
            .addCachedProperty("roles", () -> member.getRoles().stream().map(ScriptObjects::role).toList());
    }

    public static ScriptArgument message(Message message, boolean canSendMessageInSameChannel) {
        final var ctx = snowflake(message)
            .addCachedProperty("channel", () -> messageChannel(message.getChannel(), canSendMessageInSameChannel))
            .addCachedProperty("author", () -> user(message.getAuthor(), false))
            .addCachedProperty("member", () -> message.getMember() == null ? null : member(message.getMember(), false))
            .addProperty("content", message.getContentRaw());
        if (canSendMessageInSameChannel) {
            ctx.addVoidMethod("reply", 1, args -> message.reply(message(args, 0)).setAllowedMentions(ALLOWED_MENTIONS).queue());
            ctx.addVoidMethod("replyEmbed", 1, args -> {
                final var embed = embed(args, 0);
                if (embed != null) {
                    message.replyEmbeds(embed).setAllowedMentions(ALLOWED_MENTIONS).queue();
                }
            });
            ctx.addVoidMethod("replyEmbeds", -1, args -> message.replyEmbeds(args.stream(i -> embed(args, i))
                    .filter(Objects::nonNull).limit(3).toList()).setAllowedMentions(ALLOWED_MENTIONS).queue());
        }
        return ctx;
    }

    public static ScriptArgument hook(InteractionHook hook) {
        return ScriptArgument.make()
                .addVoidMethod("reply", 1, args -> hook.editOriginal(messageEdit(args, 0))
                        .setAllowedMentions(ALLOWED_MENTIONS).queue())
                .addVoidMethod("replyEmbed", 1, args -> {
                    final var embed = embed(args, 0);
                    if (embed != null) {
                        hook.editOriginalEmbeds(embed).setAllowedMentions(ALLOWED_MENTIONS).queue();
                    }
                })
                .addVoidMethod("replyEmbeds", -1, args -> hook.editOriginalEmbeds(args.stream(i -> embed(args, i))
                    .filter(Objects::nonNull).limit(3).toList()).setAllowedMentions(ALLOWED_MENTIONS).queue());
    }

    public static ScriptArgument guild(Guild guild) {
        final var context = snowflake(guild);
        context.set("name", guild.getName());
        context.set("icon", guild.getIconUrl());
        context.set("iconId", guild.getIconId());
        context.set("splash", guild.getSplashUrl());
        context.set("splashId", guild.getSplashId());
        context.set("memberCount", guild.getMemberCount());
        context.addCachedProperty("regions", () -> guild.retrieveRegions().complete());
        context.addCachedProperty("getOwner", () -> member(guild.retrieveOwner().complete(), false));
        context.addMethod("getMemberById", 1, args ->
                member(guild.retrieveMemberById(args.string(0)).complete(), false));
        context.addMethod("getTextChannelById", 1, args -> {
            final var channel = guild.getTextChannelById(args.string(0));
            return channel == null ? null : textChannel(channel, false);
        });
        context.addCachedProperty("roles", () -> guild.getRoles().stream().map(ScriptObjects::role).toList());
        context.addCachedProperty("channels", () -> guild.getChannels().stream().map(ScriptObjects::channel).toList());
        context.addCachedProperty("textChannels", () -> guild.getTextChannels().stream().map(c -> textChannel(c, false)).toList());
        return context;
    }

    public static ScriptArgument messageChannel(MessageChannelUnion union, boolean canSend) {
        if (union.getType() == ChannelType.TEXT) {
            return textChannel(union.asTextChannel(), canSend);
        }
        return messageChannel(union, canSend);
    }

    public static ScriptArgument role(Role role) {
        return snowflake(role)
                .set("name", role.getName())
                .set("color", role.getColorRaw())
                .set("timeCreated", role.getTimeCreated())
                .addMethod("isHoisted", role::isHoisted)
                .addMethod("isPublicRole", role::isPublicRole)
                .addMethod("isManaged", role::isManaged)
                .addMethod("isMentionable", role::isMentionable);
    }

    @Nullable
    public static MessageEmbed embed(ScriptArgument.CallContext context, int index) {
        return context.expectArg(index, MessageEmbed.class)
                .when(EmbedBuilder.class, EmbedBuilder::build)
                .when(Closure.class, closure -> {
                    final var builder = new EmbedBuilder();
                    closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                    closure.setDelegate(builder);
                    closure.call(builder);
                    return builder.build();
                })
                // Not so sure about this one
                .when(CharSequence.class, ch -> new EmbedBuilder().setDescription(ch).build())
                .get();
    }

    public static ScriptArgument.ArgumentExpectation<MessageCreateData> messageExp(ScriptArgument.CallContext context, int index) {
        return context.expectArg(index, MessageCreateData.class)
                .when(CharSequence.class, it -> MessageCreateData.fromContent(it.toString()))
                .when(MessageCreateBuilder.class, MessageCreateBuilder::build);
    }

    public static MessageCreateData message(ScriptArgument.CallContext context, int index) {
        return messageExp(context, index)
                .orElse(() -> MessageCreateData.fromContent(""));
    }
    public static MessageEditData messageEdit(ScriptArgument.CallContext context, int index) {
        return context.expectArg(index, MessageEditData.class)
                .flatWhen(messageExp(context, index), MessageEditData::fromCreateData)
                .when(MessageEditBuilder.class, MessageEditBuilder::build)
                .orElse(() -> MessageEditData.fromContent(""));
    }
}
