//file:noinspection GrMethodMayBeStatic
package com.matyrobbrt.kaupenbot.extensions.misc

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.db.PollsDAO
import com.sigpwned.emoji4j.core.GraphemeMatcher
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.TimeFormat
import org.apache.commons.collections4.ListUtils
import org.jetbrains.annotations.Nullable

import java.awt.*
import java.time.Duration
import java.time.Instant
import java.util.List
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

@CompileStatic
@RegisterExtension(value = 'polls', botId = 'kbot')
final class PollsExtension implements BotExtension {
    private static final List<Emoji> DEFAULT_EMOJIS = Arrays.stream('🇦 🇧 🇨 🇩 🇪'.split(' ')).<Emoji>map { Emoji.fromUnicode(it) }.toList()
    private static final String FINISH_ID = 'finish-poll-builder'
    private static final String ADD_OPTION_ID = 'add-poll-option'
    private static final Emoji END_EMOJI = Emoji.fromUnicode('🛑')

    private final Map<UUID, PollData> pending = [:]

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'poll'
            description = 'Start a poll.'
            options = [
                    new OptionData(OptionType.STRING, 'question', 'Poll question', true),
                    new OptionData(OptionType.STRING, 'duration', 'The duration the poll should last for. Default: forever. Example: 1d2s -> 1 day and 2 seconds'),
                    new OptionData(OptionType.STRING, 'options', 'The emojis to use for the options. Max: 20. Defaults to A -> E', false),
                    new OptionData(OptionType.BOOLEAN, 'multiple-choices', 'If multiple options should be allowed by this poll. Defaults to false', false)
            ]
            action {
                final emojis = getOption('options', DEFAULT_EMOJIS, { parseEmojis(it.asString) })
                if (emojis.size() > 20) {
                    replyProhibited('More than 20 options were provided!').queue()
                    return
                } else if (emojis.size() < 2) {
                    replyProhibited('Please provide at least 2 options!').queue()
                    return
                }
                final duration = getOption('duration')?.asDuration

                final id = UUID.randomUUID()
                pending[id] = new PollData(getOption('multiple-choices', false, { it.asBoolean }),
                        duration, string('question'), user.idLong, getHook(), emojis, [:])

                AtomicInteger i = new AtomicInteger()
                final rows = ListUtils.partition(emojis, 5).stream()
                    .map { ActionRow.of(it.stream().<Button>map { Emoji em ->
                        Button.secondary(ADD_OPTION_ID + '/' + id + "/${i.getAndIncrement()}", em)
                    }.toList()) }
                    .collect(Collectors.toCollection { new ArrayList<ActionRow>() })
                rows.add(ActionRow.of(Button.success(FINISH_ID + '/' + id, '✔ Done')))

                replyEphemeral('Please use the buttons below to add the options.')
                    .addComponents(rows).queue()
            }
        }
    }

    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(ButtonInteractionEvent) {
            if (!fromGuild || it.button.id === null) return
            final idSplit = it.button.id.split('/')
            if (idSplit.length < 2 || idSplit.length > 3) return
            if (idSplit[0] == ADD_OPTION_ID) {
                final data = pending[UUID.fromString(idSplit[1])]
                if (data === null) {
                    replyProhibited('Unknown poll builder!')
                    return
                }
                if (data.creator !== user.idLong) {
                    deferEdit().queue()
                    return
                }
                data.messageContent = it.message.contentRaw
                data.rows = it.message.actionRows

                final emojiIndex = data.allEmojis.indexOf(button.emoji)
                replyModal(Modal.create(ADD_OPTION_ID + '/' + idSplit[1] + '/' + emojiIndex, 'Add option')
                        .addActionRow(TextInput.create('option', 'Option', TextInputStyle.SHORT)
                            .setRequired(true)
                            .build())
                        .build())
                        .queue()
            } else if (idSplit[0] == FINISH_ID) {
                final pollId = UUID.fromString(idSplit[1])
                final data = pending[pollId]
                if (data === null) {
                    replyProhibited('Unknown poll builder!')
                    return
                }
                if (data.creator !== user.idLong) {
                    deferEdit().queue()
                    return
                }
                if (data.options.size() < 2) {
                    replyProhibited('Please provide at least 2 options!').queue()
                    return
                }
                deferEdit().queue()

                final embed = new EmbedBuilder()
                    .sentNow().setColor(Color.CYAN)
                    .setFooter(user.asTag, member.effectiveAvatarUrl)
                    .setTitle(data.question)
                final options = data.options
                        .entrySet().stream()
                        .sorted(Comparator.<Map.Entry<Emoji, String>>comparingInt {
                            data.allEmojis.indexOf(it.key)
                        })
                        .toList()

                if (data.multipleChoices) {
                    embed.appendDescription('Multiple choices are allowed!\n\n')
                }

                final Iterator<Map.Entry<Emoji, String>> iterator = options.iterator()
                while (iterator.hasNext()) {
                    final next = iterator.next()
                    embed.appendDescription("${next.key.formatted}: ${next.value}")
                    if (iterator.hasNext()) {
                        embed.appendDescription('\n')
                    }
                }

                if (data.duration !== null) {
                    embed.appendDescription("\n\nThis poll will end ${TimeFormat.RELATIVE.format(Instant.now() + data.duration)}.")
                }

                channel.sendMessageEmbeds(embed.build())
                    .flatMap { msg ->
                        RestAction.allOf(options.stream().map {
                            msg.addReaction(it.key)
                        }.toList())
                        .flatMap({ msg.channelType == ChannelType.TEXT }) { _ ->
                            msg.createThreadChannel("Discussion of ${it.member.effectiveName}’s poll")
                                .flatMap { th -> th.sendMessage("${it.user.asMention} this thread can be used for discussing your poll! React with ${END_EMOJI.formatted} to the poll message to forcefully end the poll.") }
                        }.map { msg }
                    }
                    .queue { msg ->
                        pending.remove(pollId)
                        KaupenBot.database.useExtension(PollsDAO) { db ->
                            db.add(
                                    it.channel.idLong,
                                    msg.idLong,
                                    it.user.idLong,
                                    data.duration === null ? null : Instant.now() + data.duration,
                                    false, data.question,
                                    PollsDAO.asString(options),
                                    data.multipleChoices
                            )
                        }
                        data.hook.editOriginal('*This message can be dismissed.*').setComponents(List.of())
                            .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK))
                    }
            }
        }

        jda.subscribe(ModalInteractionEvent) {
            if (!fromGuild) return
            final idSplit = modalId.split('/')
            if (idSplit.length !== 3 && idSplit[0] != ADD_OPTION_ID) return
            final data = pending[UUID.fromString(idSplit[1])]
            if (data === null) {
                replyProhibited('Unknown poll builder!')
                return
            }
            final emoji = data.allEmojis[Integer.parseInt(idSplit[2])]
            final option = it.getValue('option')?.asString
            data.options.put(emoji, option)

            final action = data.hook.editOriginal(data.messageContent + "\n${emoji.formatted}: ${option}")
            final List<ActionRow> rows = []
            data.rows.each {
                final List<ItemComponent> components = []
                for (final comp : it) {
                    if (comp instanceof Button && comp.emoji == emoji) {
                        components.add(comp.asDisabled())
                    } else {
                        components.add(comp)
                    }
                }
                rows.add(ActionRow.of(components))
            }
            action.setComponents(rows).flatMap { deferEdit() }.queue()
        }

        jda.subscribe(MessageReactionAddEvent) {
            final owner = KaupenBot.database.withExtension(PollsDAO) { db ->
                db.getOwner(it.channel.idLong, it.messageIdLong)
            }
            if (owner === null) return
            if (it.emoji == END_EMOJI) {
                if (owner !== null && it.user.idLong == owner) {
                    if (!KaupenBot.database.withExtension(PollsDAO) { db ->
                        db.isFinished(it.channel.idLong, it.messageIdLong)
                    }) {
                        endPoll(it.channel.idLong, it.messageIdLong)
                    }
                }
            } else if (!KaupenBot.database.withExtension(PollsDAO) { db -> db.isMultipleChoices(it.channel.idLong, it.messageIdLong)}) {
                RestAction.allOf(it.retrieveMessage()
                        .flatMap { it.getReactions() }
                        .flatMap { it})
                it.retrieveMessage()
                    .flatMap {
                        RestAction.allOf(it.reactions.stream().map {
                            it.retrieveUsers()
                        }.toList())
                    }
                    .queue { users ->
                        if (users.findAll { us -> it.user in us }.size() > 1) {
                            it.reaction.removeReaction(it.user).queue()
                        }
                    }
            }
        }
    }

    @Override
    void scheduleTasks(ScheduledExecutorService service) {
        service.scheduleAtFixedRate({
                KaupenBot.database.useExtension(PollsDAO) {
                    it.getExpiredSuggestions(Instant.now(), false)
                        .each {
                            endPoll(it.channelId(), it.messageId())
                        }
                }
        }, 0, 5, TimeUnit.MINUTES)
    }

    void endPoll(long channelId, long messageId) {
        final options = PollsDAO.fromString(KaupenBot.database.withExtension(PollsDAO) {
            it.getOptions(channelId, messageId)
        }).stream().collect(Collectors.<Map.Entry<Emoji, String>, Emoji, String>toMap({ it.key }, { it.value }))
        KaupenBot.jda.getChannelById(MessageChannel, channelId)
            .retrieveMessageById(messageId)
            .flatMap { msg ->
                final Map<Emoji, Integer> votes = msg.reactions.stream()
                    .collect(Collectors.<MessageReaction, Emoji, Integer>toMap({ it.emoji },
                            { it.self ? it.count - 1 : it.count }))
                    .sort { a, b -> b.value <=> a.value }
                final embed = new EmbedBuilder(msg.embeds[0])
                    .setColor(Color.MAGENTA)
                embed.setDescription('*Poll has ended!*\n')

                final Iterator<Map.Entry<Emoji, Integer>> iterator = votes.iterator()
                while (iterator.hasNext()) {
                    final next = iterator.next()
                    final desc = options[next.key]
                    if (desc !== null) {
                        embed.appendDescription("**${next.value} votes**: ${next.key.formatted}: ${desc}")
                        if (iterator.hasNext()) {
                            embed.appendDescription('\n')
                        }
                    }
                }

                KaupenBot.database.useExtension(PollsDAO) {
                    it.markFinished(channelId, messageId, true)
                }

                msg.editMessageEmbeds(embed.build())
                    .flatMap { it.clearReactions() }
                    .flatMap({ msg.startedThread !== null }) { msg.startedThread.sendMessage('This poll has ended!')
                        .flatMap { msg.startedThread.manager.setLocked(true).setArchived(true) } }
            }.queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE))
    }

    static List<Emoji> parseEmojis(String str) {
        final Set<Emoji> emojis = []

        final matcher = Message.MentionType.EMOJI.pattern.matcher(str)
        while (matcher.find()) {
            emojis.add(Emoji.fromFormatted(matcher.group()))
        }

        final graphemeMatcher = new GraphemeMatcher(str)
        while (graphemeMatcher.find()) {
            final match = graphemeMatcher.grapheme()
            emojis.add(Emoji.fromUnicode(match.toString()))
        }

        final list = new ArrayList<Emoji>(emojis)
        list.sort(Comparator.<Emoji>comparingInt {
            str.indexOf(it.formatted)
        })
        return list
    }

    @CompileStatic
    static final class PollData {
        final boolean multipleChoices
        @Nullable
        final Duration duration
        final String question
        final List<Emoji> allEmojis
        final Map<Emoji, String> options
        final long creator
        final InteractionHook hook

        List<ActionRow> rows
        String messageContent

        PollData(boolean multipleChoices, Duration duration, String question, long creator, InteractionHook hook, List<Emoji> allEmojis, Map<Emoji, String> options) {
            this.duration = duration
            this.question = question
            this.creator = creator
            this.options = options
            this.allEmojis = allEmojis
            this.hook = hook
            this.multipleChoices = multipleChoices
        }
    }
}
