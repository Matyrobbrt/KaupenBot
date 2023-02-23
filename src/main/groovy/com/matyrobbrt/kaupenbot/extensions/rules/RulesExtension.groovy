package com.matyrobbrt.kaupenbot.extensions.rules

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.JavaCalls
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookManager
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction

import java.util.concurrent.CompletableFuture

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'rules')
class RulesExtension implements BotExtension {
    public static final WebhookManager WEBHOOKS = WebhookManager.of('Rules')
    public static final GroovyShell SHELL = new GroovyShell()

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'updaterules'
            require(Permission.MANAGE_ROLES)
            description = 'Update the server rules'
            options = [
                    new OptionData(OptionType.STRING, 'rules', 'A link to the rules', true),
                    new OptionData(OptionType.CHANNEL, 'channel', 'The rules channel', true)
            ]
            guildOnly = true
            action {
                String data
                try (final var is = new URL(string('rules')).openStream()) {
                    data = new String(is.readAllBytes());
                } catch (IOException e) {
                    deferReply(true)
                            .setContent("There was an exception running the command: " + e.getLocalizedMessage())
                            .queue()
                    return
                }

                deferReply().queue()

                final Runnable whenDeleted = () -> {
                    final webhook = WEBHOOKS.getWebhook(it.messageChannel('channel'))
                    final builder = new RulesBuilder()
                    final script = SHELL.parse(data)
                    script.setBinding(new Binding(['builder': builder]))
                    script.run()

                    final messages = builder.build()
                    if (messages.isEmpty()) {
                        it.hook.sendMessage("Could not update rules! Empty list of messages was gathered.").setEphemeral(true).queue();
                        return;
                    }
                    final action = JavaCalls.sendMessages(webhook, it.guild.name, it.guild.iconUrl, messages)
                    action.whenComplete(($$, e) -> {
                        if (e != null) {
                            KaupenBot.log.error('Exception trying to update rules: ', e)
                            it.hook.sendMessage("There was an exception executing that command: " + e.getLocalizedMessage())
                                    .setEphemeral(true).queue()
                        } else {
                            it.hook.sendMessage('Rules updated!').queue()
                        }
                    })
                }

                final rulesChannel = messageChannel('channel')
                if (rulesChannel.getLatestMessageIdLong() != 0) {
                    rulesChannel.getHistoryBefore(rulesChannel.getLatestMessageIdLong(), 100)
                            .map(MessageHistory::getRetrievedHistory)
                            .submit()
                            .whenComplete((messages, throwable) -> {
                                if (messages.size() >= 2) {
                                    CompletableFuture.allOf(rulesChannel.purgeMessages(messages)
                                            .toArray(CompletableFuture[]::new))
                                            .thenApply(s -> rulesChannel.deleteMessageById(rulesChannel.getLatestMessageId()).submit())
                                    .whenComplete(($1, $2) -> { whenDeleted.run() })
                                } else if (!messages.isEmpty()) {
                                    RestAction.allOf(messages.stream().map(Message::delete).toList())
                                        .flatMap { rulesChannel.deleteMessageById(rulesChannel.latestMessageIdLong) }
                                        .queue({whenDeleted.run()}, {whenDeleted.run()})
                                } else {
                                    rulesChannel.retrieveMessageById(rulesChannel.latestMessageIdLong)
                                            .queue({whenDeleted.run()}, {whenDeleted.run()})
                                }
                            })
                } else {
                    whenDeleted.run()
                }
            }
        }
    }
}
