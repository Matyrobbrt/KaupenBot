package com.matyrobbrt.kaupenbot.extensions.misc

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.vdurmont.emoji.EmojiParser
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.requests.RestAction
import org.apache.commons.collections4.ListUtils

import java.awt.*
import java.util.List
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

@CompileStatic
@RegisterExtension(value = 'polls', botId = 'kbot')
final class PollsExtension implements BotExtension {
    private static final List<Emoji> DEFAULT_EMOJIS = Arrays.stream('🇦 🇧 🇨 🇩 🇪'.split(' ')).<Emoji>map { Emoji.fromUnicode(it) }.toList()
    private static final String FINISH_ID = 'finish-poll-builder'
    private static final String ADD_OPTION_ID = 'add-poll-option'

    private final Map<UUID, PollData> pending = [:]

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        manager.addCommand {
            name = 'poll'
            description = 'Start a poll.'
            options = [
                    new OptionData(OptionType.STRING, 'question', 'Poll question', true),
                    new OptionData(OptionType.STRING, 'options', 'The emojis to use for the options. Max: 20. Defaults to A -> E', false)
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

                final id = UUID.randomUUID()
                pending[id] = new PollData(string('question'), user.idLong, emojis, [:])

                AtomicInteger i = new AtomicInteger()
                final rows = ListUtils.partition(emojis, 5).stream()
                    .map { ActionRow.of(it.stream().<Button>map { Emoji em ->
                        Button.secondary(ADD_OPTION_ID + '/' + id + "/${i.getAndIncrement()}", em)
                    }.toList()) }
                    .collect(Collectors.toCollection { new ArrayList<ActionRow>() })
                rows.add(ActionRow.of(Button.success(FINISH_ID + '/' + id, '✔ Done')))

                reply('Please use the buttons below to add the options.')
                    .addComponents(rows)
                    .flatMap { it.retrieveOriginal() }
                    .queue((msg) -> pending.get(id).setMessageId(msg.idLong))
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
                final Iterator<Map.Entry<Emoji, String>> iterator = options.iterator()
                while (iterator.hasNext()) {
                    final next = iterator.next()
                    embed.appendDescription("${next.key.formatted}: ${next.value}")
                    if (iterator.hasNext()) {
                        embed.appendDescription('\n')
                    }
                }

                channel.retrieveMessageById(data.messageId)
                    .flatMap { it.editMessageEmbeds(embed.build()).setContent(null).setComponents(List.of()) }
                    .flatMap { msg ->
                         RestAction.allOf(options.stream().map {
                             msg.addReaction(it.key)
                         }.toList())
                    }.queue { pending.remove(pollId) }
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
            channel.retrieveMessageById(data.messageId)
                .flatMap {
                    final List<ActionRow> rows = []
                    it.actionRows.each {
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
                    it.editMessage(it.contentRaw + "\n${emoji.formatted}: ${option}")
                        .setComponents(rows)
                }
                .flatMap { deferEdit() }
                .queue()
        }
    }

    static List<Emoji> parseEmojis(String str) {
        final Set<Emoji> emojis = []
        final matcher = Message.MentionType.EMOJI.pattern.matcher(str)
        while (matcher.find()) {
            emojis.add(Emoji.fromFormatted(matcher.group()))
        }
        for (final emoji : EmojiParser.extractEmojis(str)) {
            emojis.add(Emoji.fromUnicode(emoji))
        }
        final list = new ArrayList<Emoji>(emojis)
        list.sort(Comparator.<Emoji>comparingInt {
            str.indexOf(it.formatted)
        })
        return list
    }

    @CompileStatic
    static final class PollData {
        long messageId
        final String question
        final List<Emoji> allEmojis
        final Map<Emoji, String> options
        final long creator

        PollData(String question, long creator, List<Emoji> allEmojis, Map<Emoji, String> options) {
            this.question = question
            this.creator = creator
            this.options = options
            this.allEmojis = allEmojis
        }
    }
}
