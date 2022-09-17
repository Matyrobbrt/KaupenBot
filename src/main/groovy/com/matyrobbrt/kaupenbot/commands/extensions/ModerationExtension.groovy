package com.matyrobbrt.kaupenbot.commands.extensions

import com.matyrobbrt.kaupenbot.commands.api.BotExtension
import com.matyrobbrt.kaupenbot.commands.api.CommandManager
import com.matyrobbrt.kaupenbot.commands.api.RegisterExtension
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function

@CompileStatic
@RegisterExtension
final class ModerationExtension implements BotExtension {
    @Override
    void fillCommands(CommandManager manager) {
        manager.addCommand {
            name = 'softban'
            description = 'Bans and unbans a member, deleting their messages.'
            guildOnly = true
            options = [
                    new OptionData(OptionType.USER, 'user', 'The user to soft-ban.', true),
                    new OptionData(OptionType.INTEGER, 'days', 'The amount of days to delete messages for.').setRequiredRange(1, 7)
            ]
            require Permission.BAN_MEMBERS
            checkIf({ it.member('user') !== null }, 'Unknown user!')
            action {
                final days = integer('days', 1)
                final user = member('user')
                deferReply().queue()
                user.ban(days, "Soft ban issued by ${it.user.asTag} (${it.user.id})")
                        .delay(1, TimeUnit.SECONDS)
                        .flatMap { guild.unban(user) }
                        .flatMap { hook.editOriginal('✅ User has been successfully soft banned!') }
                        .queue()
            }
        }

        /* TODO make this work
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
                final int amount = integer('amount')
                final user = user('user')

                CompletableFuture<List<Message>> messages
                if (user !== null) {
                    messages = getMessagesByUser(messageChannel, amount, user)
                } else {
                    messages = getMessages(messageChannel, amount)
                }
                deferReply(true).queue()
                messages.thenAccept(msgs -> {
                    hook.editOriginal("I have found ${msgs.size()} messages, I will now start purging!")
                            .mentionRepliedUser(false)
                            .queue()

                    final List<AtomicBoolean> complete = new ArrayList<>()
                    msgs.forEach(msg -> complete.add(new AtomicBoolean(false)))
                    CompletableFuture.allOf(channel.purgeMessages(msgs).toArray(CompletableFuture[]::new))
                            .thenAccept(action -> channel.sendMessage(
                                    "$member.asMention ✅ I have successfully purged ${msgs.size()} messages!")
                                    .queue())
                })
            }
        } */
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
