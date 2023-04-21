package com.matyrobbrt.kaupenbot.extensions.misc

import club.minnced.discord.webhook.send.AllowedMentions
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookManager
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender
import com.matyrobbrt.kaupenbot.common.util.webhooks.WebhookMessageSender.Attachment
import com.matyrobbrt.kaupenbot.listener.ThreadListeners
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'helpChannels')
class HelpChannelsExtension implements BotExtension {
    private static final WebhookManager WEBHOOKS = WebhookManager.of('How to ask for help', AllowedMentions.none(), { it.manager.setAvatar(Icon.from(KaupenBot.class.getResourceAsStream('/images/ask_for_help.png'))).queue() })

    private static final String NO_VERSION = 'Please do not create posts without a version tag.'
    private static final Function<Boolean, String> CREATION_MESSAGE = isForge -> """
**__Here are some tips on how to ask for help effectively__**
- [Don't ask if you can ask a question](<https://iki.fi/sol/dontask.html>), it's needless. Ask your question and get an answer faster!
- [The XY Problem](<https://xyproblem.info/>) - Don't ask about your attempted solution, rather, ask about your actual problem.
- Make sure to provide game logs if your issue is a crash.
- Don't beg for help, or ping someone! You're not entitled to an answer, people will help you if they want to.
- Make sure to provide as much detail as you can about what you're trying to do:
  👍 `I want to heal the player when they jump`
  👎 `I want to call method X when a player does Y`
- If you have issues with textures or models, look in the console for errors. (`!consolejson` for more information)

**__How to share code__**
You can share files by posting them as an attachment on Discord, and then you can react with 🗒️ to the message to create a gist. ${isForge ? '\nWhen providing either game logs, or crash logs, by reacting with ❓ to the message with the logs, they will be analyzed by a bot in order to help with common issues.' : ''}
For sharing code, use codeblocks (replacing `language` with the language the code is written in, like `java` or `json`):
\\`\\`\\`language
Code here
\\`\\`\\`

When you have received an answer that satisfies you, make sure to thank everyone who helped you and close the thread as shown below!
""".toString().trim()
    private static final String NOT_MODDING_HELP = 'This channel is not for Minecraft support! Get the roles you need from <#858641026747334666> and ask in the relevant channels!'

    private static final List<Attachment> ATTACHMENTS = List.of(new Attachment('close_thrad.png', KaupenBot.class.getResourceAsStream('/images/close_thread.png').withCloseable { it.readAllBytes() }))

    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(ChannelCreateEvent) {
            if (it.channelType !== ChannelType.GUILD_PUBLIC_THREAD) return
            final ThreadChannel thread = it.channel.asThreadChannel()
            if (thread.parentChannel.idLong !in KaupenBot.config.channels.helpChannels) return

            if (thread.parentChannel.idLong === KaupenBot.config.channels.programmingHelpChannel) {
                if (thread.appliedTags.stream().anyMatch {
                    it.name.equalsIgnoreCase('minecraft')
                }) {
                    thread.sendMessage(NOT_MODDING_HELP)
                            .flatMap { thread.manager.setLocked(true).setArchived(true) }
                            .queue()
                    return
                }
            }

            if (thread.appliedTags.stream().noneMatch {
                it.name.startsWith('Version:') || it.name.startsWith('1.1')
            } && thread.parentChannel.asForumChannel().availableTags.stream().anyMatch {
                it.name.startsWith('Version:') || it.name.startsWith('1.1')
            }) {
                thread.sendMessage(NO_VERSION)
                    .flatMap { thread.manager.setLocked(true).setArchived(true) }
                    .queue()
                return
            }

            WebhookMessageSender.send(WEBHOOKS.getWebhook(thread), CREATION_MESSAGE.apply(thread.parentChannel.name.containsIgnoreCase('forge')), ATTACHMENTS)
                .thenAccept {
                    ThreadListeners.addMods(thread).queue()
                }.exceptionHandling()
        }

        jda.subscribe(ChannelUpdateAppliedTagsEvent) {
            final thread = it.channel.asThreadChannel()
            if (thread.parentChannel.idLong === KaupenBot.config.channels.programmingHelpChannel && it.newTags.stream().anyMatch {
                it.name.toLowerCase(Locale.ROOT).contains('minecraft')
            }) {
                thread.sendMessage(NOT_MODDING_HELP)
                        .flatMap { thread.manager.setLocked(true).setArchived(true) }
                        .queue()
            }
        }
    }

    @Override
    void scheduleTasks(ScheduledExecutorService service) {
        service.scheduleAtFixedRate({
            final Instant _3DaysAgo = Instant.now().minus(3, ChronoUnit.DAYS)
            KaupenBot.config.channels.helpChannels.each { id ->
                final ForumChannel forum = KaupenBot.jda.getForumChannelById(id)
                if (forum === null) return

                forum.threadChannels.each {
                    final Consumer<Message> lastMessage = { Message last ->
                        if (last.timeCreated.toInstant().isBefore(_3DaysAgo)) {
                            it.manager.setArchived(true).reason('3 days without activity').queue()
                        }
                    }

                    final his = it.history.retrievedHistory
                    if (his.isEmpty()) {
                        it.history.retrievePast(1).queue {
                            if (!it.isEmpty()) {
                                lastMessage.accept(it.get(0))
                            }
                        }
                    } else {
                        lastMessage.accept(his.last())
                    }
                }
            }
        }, 3, 12 * 60, TimeUnit.MINUTES)
    }
}
