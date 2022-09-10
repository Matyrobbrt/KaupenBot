package com.matyrobbrt.kaupenbot.modmail

import com.matyrobbrt.kaupenbot.modmail.db.TicketsDAO
import com.matyrobbrt.kaupenbot.util.webhooks.WebhookManager
import com.matyrobbrt.kaupenbot.util.webhooks.WebhookMessageSender
import groovy.transform.CompileStatic
import kotlin.Pair
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull
import java.awt.*
import java.util.function.Function

@CompileStatic
final class ModMailListener extends ListenerAdapter {
    public static final WebhookManager WEBHOOKS = WebhookManager.of('ModLogs')
    static final Emoji SUCCESS_EMOJI = Emoji.fromUnicode('U+2705')
    static final Emoji FAILED_EMOJI = Emoji.fromUnicode('U+274C')

    @Override
    void onMessageReceived(@NotNull @Nonnull MessageReceivedEvent event) {
        if (ModMail.config.prefixes.any {
            event.message.contentRaw.startsWith(it)
        }) return
        if (event.fromGuild) {
            handleGuild(event)
        } else {
            handleDm(event)
        }
    }

    @Override
    void onChannelDelete(@NotNull @Nonnull ChannelDeleteEvent event) {
        if (event.channelType !== ChannelType.GUILD_PUBLIC_THREAD) return

        final id = event.channel.idLong
        final thread = event.channel.asThreadChannel()
        if (thread.parentChannel.idLong == ModMail.config.loggingChannel) {
            final owner = ModMail.database.withExtension(TicketsDAO) {
                it.getUser(id, true)
            }
            if (owner === null) return
            ModMail.database.useExtension(TicketsDAO) {
                it.removeThread(id)
            }
            thread.retrieveParentMessage()
                .flatMap { msg -> event.guild.retrieveMember(UserSnowflake.fromId(owner)).map {new Pair<>(msg, it) }  }
                .flatMap {
                    final member = it.component2()
                    it.component1().editMessage(
                            MessageEditData.fromEmbeds(embed {
                                sentNow()
                                color = Color.GREEN
                                title = '~~New Ticket~~ Ticked has been deleted'
                                description = "Ticked has been deleted."
                                setFooter("${member.user.asTag} | ${member.user.id}", member.user.effectiveAvatarUrl)
                            })
                    )
                }.queue()
        }
    }

    @Override
    void onChannelUpdateArchived(@NotNull @Nonnull ChannelUpdateArchivedEvent event) {
        // TODO handle archival
    }

    private static MessageCreateData makeInitialMessage(Member member) {
        return new MessageCreateBuilder()
            .setContent(Arrays.stream(ModMail.config.pingRoles).map { "<@&${it}>" }.toList().join(" "))
            .addEmbeds(embed {
                color = 0xadd8e6
                title = 'New Ticket'
                if (ModMail.config.repliesOnly) {
                    appendDescription('Use the `/reply`, `/areply` or the respective prefix commands in order to reply. ')
                } else {
                    appendDescription('Type a message in this channel to reply. ')
                }
                appendDescription("Messages starting with the server's prefixes will be ignored.\nUse the `/close` or `/aclose` command to close the ticket.")
                addField('Server prefixes', Arrays.stream(ModMail.config.prefixes).map { "`${it}`" }.toList().join(", "), true)

                addField('User', "${member.asMention} (${member.id})", true)
                addField('Roles', member.roles.stream().map { it.asMention }.toList().join(" "), true)
            })
            .build()
    }

    private static void handleDm(final MessageReceivedEvent event) {
        final author = event.author
        if (author.bot) return
        ModMail.guild.retrieveMember(event.author).queue({ member ->
            if (member.roles.any { it.idLong === ModMail.config.blacklistedRole }) {
                event.message.reply("You're blacklisted from tickets!").queue()
                return
            }
            final active = ModMail.activeTicket(author.idLong)
            if (active !== null) {
                final thread = ModMail.guild.getThreadChannelById(active)
                if (thread != null) {
                    WebhookMessageSender.send(
                            WEBHOOKS[thread],
                            createMessageReceived(event.message),
                            event.message.author.name,
                            event.message.author.effectiveAvatarUrl
                    ).thenAccept {
                        ModMail.database.useExtension(TicketsDAO) { dao ->
                            dao.insertMessageAssociation(it.id, event.message.idLong)
                        }
                    }.thenCompose { event.message.addReaction(SUCCESS_EMOJI).submit() }
                            .whenComplete { it, t ->
                                if (t != null)
                                    event.message.addReaction(FAILED_EMOJI).queue()
                            }
                            .exceptionHandling()
                } else {
                    createThread(event, member)
                }
            } else {
                createThread(event, member)
            }
        }, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER))
    }

    private static MessageEmbed createMessageReceived(Message message) {
        return embed {
            color = Color.GREEN
            title = 'Message Received'
            timestamp = message.timeCreated
            description = message.contentRaw
            footer = message.author.id
        }
    }

    private static void createThread(final MessageReceivedEvent event, Member member) {
        final embed = new EmbedBuilder().tap {
            sentNow()
            color = Color.GREEN
            title = 'New Ticket'
            setFooter("${event.author.asTag} | ${event.author.id}", event.author.effectiveAvatarUrl)
        }
        ModMail.log(MessageCreateData.fromEmbeds(embed.build())).flatMap {
            it.createThreadChannel("${event.author.name}’s ticket")
        }.flatMap { th ->
            th.retrieveParentMessage().flatMap {
                it.editMessage(MessageEditData.fromEmbeds(embed.tap {
                    description = th.asMention
                }.build()))
            }
            .flatMap { th.sendMessage(makeInitialMessage(member)) }
            .map { th }
        }.submit().thenCompose {
            ModMail.database.useExtension(TicketsDAO) { dao ->
                dao.insertThread(event.author.idLong, it.idLong, true)
            }
            WebhookMessageSender.send(WEBHOOKS[it], createMessageReceived(event.message), event.message.author.name, event.message.author.effectiveAvatarUrl)
        }.thenAccept {
            ModMail.database.useExtension(TicketsDAO) { dao ->
                dao.insertMessageAssociation(it.id, event.message.idLong)
            }
        }.thenCompose { event.message.addReaction(SUCCESS_EMOJI).submit() }
        .whenComplete { it, t ->
            if (t != null)
                event.message.addReaction(FAILED_EMOJI).queue()
        }.exceptionHandling()
    }

    private static void handleGuild(final MessageReceivedEvent event) {
        if (ModMail.config.repliesOnly) return
        if (!event.channel.type.isThread() || event.author.bot) return
        final thread = event.channel.asThreadChannel()
        if (thread.parentChannel.idLong !== ModMail.config.loggingChannel) return
        final reference = event.message.messageReference?.with { ref ->
            ModMail.database.withExtension(TicketsDAO) {
                it.getAssociatedMessage(ref.messageIdLong)
            }
        }
        doReply(thread, event.member, ModMail.config.anonymousByDefault, reference, event.message.contentRaw, {
            event.message.addReaction(SUCCESS_EMOJI).queue()
        }, {
            event.message.reply(it).flatMap { event.message.addReaction(FAILED_EMOJI) }
        })
    }

    static void doReply(ThreadChannel thread, Member moderator, boolean anonymous, Long reference, String message, Runnable onSuccess, Function<String, RestAction> onFailure, boolean checkGoodThread = false) {
        final userId = ModMail.database.withExtension(TicketsDAO) {
            it.getUser(thread.idLong, true)
        }
        if (userId === null) {
            if (checkGoodThread) {
                onFailure.apply('This thread is not a ticket!').queue()
            }
            return
        }
        moderator.guild.retrieveMemberById(userId)
                .flatMap { it.user.openPrivateChannel() }
                .flatMap {
                    it.sendMessageEmbeds(embed {
                        color = Color.RED
                        title = 'Message Received'
                        if (anonymous) {
                            setAuthor('Anonymous')
                        } else {
                            setAuthor(moderator.user.asTag, null, moderator.user.effectiveAvatarUrl)
                        }
                        description = message
                        setFooter("${moderator.guild.name} | ${moderator.guild.id}", moderator.guild.iconUrl)
                    }).setMessageReference(reference?.toString())
                }
                .queue({ onSuccess.run() }, new ErrorHandler()
                        .handle(ErrorResponse.CANNOT_SEND_TO_USER, {
                            onFailure.apply('Cannot send messages to this user!\nPlease contact them manually and ask them to open their DMs.').queue()
                        })
                        .handle(ErrorResponse.UNKNOWN_MEMBER, {
                            onFailure.apply('The ticker owner has left the guild!')
                                    .flatMap { thread.manager.setArchived(true) }
                                    .queue {
                                        ModMail.database.useExtension(TicketsDAO) {
                                            it.markActive(thread.idLong, false)
                                        }
                                    }
                        }))
    }
}
