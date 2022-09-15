package com.matyrobbrt.kaupenbot.commands.moderation

import com.jagrosh.jdautilities.command.SlashCommand
import com.matyrobbrt.kaupenbot.util.CallbackCommand
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

@CompileStatic
class PurgeCommand extends SlashCommand implements CallbackCommand {
    PurgeCommand() {
        name = 'purge'
        help = 'Purge messages from a channel.'
        userPermissions = new Permission[] {
            Permission.MESSAGE_MANAGE
        }
        options = [
                new OptionData(OptionType.INTEGER, 'amount', 'The amount of messages to delete.').setRequiredRange(1, 500),
                new OptionData(OptionType.USER, 'user', 'The user to delete messages from.', false)
        ]
        guildOnly = true

        setCallback {
            final int amount = integer('amount')
            final user = user('user')

            CompletableFuture<List<Message>> messages
            if (user !== null) {
                messages = getMessagesByUser(messageChannel, amount, user)
            } else {
                messages = getMessages(messageChannel, amount)
            }
            messages.thenAccept(msgs -> {
                deferReply(true).setContent("I have found ${msgs.size()} messages, I will now start purging!")
                        .mentionRepliedUser(false)
                        .queue()

                final List<AtomicBoolean> complete = new ArrayList<>()
                msgs.forEach(msg -> complete.add(new AtomicBoolean(false)))
                CompletableFuture.allOf(channel.purgeMessages(msgs).toArray(CompletableFuture[]::new))
                        .thenAccept(action -> channel.sendMessage(
                                "$member.asMention ✅ I have successfully purged ${msgs.size()} messages!")
                                .queue())
            });
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
