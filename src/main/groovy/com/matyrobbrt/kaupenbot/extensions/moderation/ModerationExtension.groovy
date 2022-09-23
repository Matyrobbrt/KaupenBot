package com.matyrobbrt.kaupenbot.extensions.moderation

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'moderation')
final class ModerationExtension implements BotExtension {
    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'softban'
            description = 'Bans and unbans a member, deleting their messages.'
            guildOnly = true
            options = [
                    new OptionData(OptionType.USER, 'user', 'The user to soft-ban.', true),
                    new OptionData(OptionType.STRING, 'reason', 'The reason for the soft-ban', true),
                    new OptionData(OptionType.INTEGER, 'days', 'The amount of days to delete messages for.').setRequiredRange(1, 7)
            ]
            require Permission.BAN_MEMBERS
            checkIf({ it.member('user') !== null }, 'Unknown user!')
            checkHierarchy('user')
            action {
                final days = integer('days', 1)
                final user = member('user')
                deferReply().queue()
                user.ban(days, "Soft ban issued by ${it.user.asTag}: ${string('reason')}")
                        .delay(1, TimeUnit.SECONDS)
                        .flatMap { guild.unban(user) }
                        .flatMap { hook.editOriginal('✅ User has been successfully soft banned!') }
                        .queue()
            }
        }

        manager.addCommand {
            name = 'purge'
            description = 'Purge messages from a channel.'
            options = [
                    new OptionData(OptionType.INTEGER, 'amount', 'The amount of messages to delete.').setRequiredRange(1, 500),
                    new OptionData(OptionType.USER, 'user', 'The user to delete messages from.', false)
            ]
            guildOnly = true
            require Permission.MESSAGE_MANAGE
            failIf { it.integer('amount') > 500 }
            action {
                final amount = integer('amount')
                final user = user('user')

                CompletableFuture<List<Message>> messages
                if (user !== null) {
                    messages = getMessagesByUser(messageChannel, amount, user)
                } else {
                    messages = getMessages(messageChannel, amount)
                }
                deferReply(true).queue()
                messages.thenAccept(msgs -> {
                    it.getHook().sendMessage("Found ${msgs.size()} messages. Started purge...")
                            .mentionRepliedUser(false)
                            .queue()

                    if (msgs.isEmpty()) return
                    List<RestAction<Void>> list = new ArrayList<>()
                    TreeSet<Long> sortedIds = new TreeSet<>(Comparator.reverseOrder())
                    for (final msg : msgs)
                        if (msg.type.canDelete())
                            sortedIds.add(msg.idLong)
                    for (long messageId : sortedIds)
                        list.add(it.getMessageChannel().deleteMessageById(messageId))
                    if (!list.isEmpty()) {
                        final sendIn = it.getMessageChannel()
                        final mention = it.getMember().getAsMention()
                        RestAction.allOf(list).flatMap((deleted) ->
                            sendIn.sendMessage("$mention ✅ Successfully purged ${deleted.size()} messages!")
                        ).queue()
                    }
                })
            }
        }
    }

    static CompletableFuture<List<Message>> getMessages(MessageChannel channel, int amount) {
        return channel.getIterableHistory().takeAsync(amount) // Collect 'amount' messages
                .thenApply(Function.identity())
    }

    static CompletableFuture<List<Message>> getMessagesByUser(MessageChannel channel, int amount, User user) {
        return channel.getIterableHistory().takeAsync(amount) // Collect 'amount' messages
                .thenApply(list -> list.stream().filter(m -> m.getAuthor() == user) // Filter messages by author
                        .toList())
    }
}
