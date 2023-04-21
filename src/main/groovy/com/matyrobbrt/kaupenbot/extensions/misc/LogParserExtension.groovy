package com.matyrobbrt.kaupenbot.extensions.misc

import com.matyrobbrt.jdahelper.components.ComponentListener
import com.matyrobbrt.jdahelper.components.ComponentManager
import com.matyrobbrt.jdahelper.components.context.ButtonInteractionContext
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.ExtensionArgument
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.JavaCalls
import com.matyrobbrt.kaupenbot.logparser.Log
import com.matyrobbrt.kaupenbot.logparser.LogType
import com.matyrobbrt.kaupenbot.logparser.ResultHandler
import com.matyrobbrt.kaupenbot.util.util.Gist
import com.matyrobbrt.kaupenbot.util.util.GistUtils
import groovy.transform.CompileStatic
import groovy.transform.ImmutableOptions
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder

import java.awt.*
import java.time.Instant
import java.util.List
import java.util.function.BiFunction
import java.util.function.Function

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'logParser')
final class LogParserExtension implements BotExtension {
    private static final List<String> ACCEPTED_EXTENSIONS = List.of(
            'log', 'txt'
    )
    private static final UnicodeEmoji EMOJI = Emoji.fromUnicode('❓')
    private static final Map<Integer, EmojiUnion> NR_TO_EMOJI = [
            1: Emoji.fromFormatted('1️'),
            2: Emoji.fromFormatted('2️'),
            3: Emoji.fromFormatted('3️'),
            4: Emoji.fromFormatted('4️'),
            5: Emoji.fromFormatted('5️'),
            6: Emoji.fromFormatted('6️'),
            7: Emoji.fromFormatted('7️'),
            8: Emoji.fromFormatted('8️'),
            9: Emoji.fromFormatted('9️'),
    ]

    private final ComponentListener components
    private final String gistToken
    LogParserExtension(@ExtensionArgument('env') Dotenv env, @ExtensionArgument('components') ComponentManager componentManager) {
        this.gistToken = env.get('GIST_TOKEN')

        this.components = ComponentListener.builder('log-parser', componentManager.&addListener)
                .onButtonInteraction(this.&onButton)
                .build()
    }

    private void onButton(final ButtonInteractionContext ctx) {
        final issueEmbedStr = ctx.arguments.get(0)
        final builder = new EmbedBuilder(((JDAImpl) ctx.getJDA()).entityBuilder.createMessageEmbed(DataObject.fromJson(issueEmbedStr)))
        builder.setTimestamp(Instant.now()).setTitle("Issue number ${ctx.arguments[1]}", ctx.arguments[2])
        ctx.reply(MessageCreateData.fromEmbeds(builder.build()))
            .mentionRepliedUser(false).setEphemeral(false)
            .flatMap {
                ctx.getJDA().getMessageByLink(ctx.arguments[2])
            }
            .flatMap {
                JavaCalls.disableButtonWithID(it, ctx.button.id)
            }.queue()
        ctx.deleteComponent()
    }

    @SuppressWarnings('GroovyVariableNotAssigned')
    @Override
    void subscribeEvents(JDA jda) {
        if (gistToken === null) return

        jda.subscribe(MessageReceivedEvent) { event ->
            for (final attach : event.message.attachments) {
                if (attach.fileExtension in ACCEPTED_EXTENSIONS || attach.fileName.contains('crash')) {
                    try (final is = URI.create(attach.proxy.url).toURL().openStream()) {
                        final reader = new BufferedReader(new InputStreamReader(is))
                        final type = LogType.detectType(reader.readLine())

                        if (type !== null) {
                            event.message.addReaction(EMOJI).queue()
                            return
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        jda.subscribe(MessageReactionAddEvent) { event ->
            if (event.emoji == EMOJI && event.userIdLong !== event.getJDA().selfUser.idLong) {
                event.retrieveMessage().queue {
                    if (it.getReaction(EMOJI).self) {
                        event.reaction.clearReactions().queue { _ -> it.reply('Analysing log... Please wait.').queue { newMsg ->
                            final gist = new Gist(it.contentRaw, false)
                            String content
                            for (final attach : it.attachments) {
                                if (attach.fileExtension in ACCEPTED_EXTENSIONS || attach.fileName.contains('crash')) {
                                    try (final is = URI.create(attach.proxy.url).toURL().openStream()) {
                                        content = GistUtils.readInputStream(is)
                                        gist.addFile('log', content)
                                        break
                                    }
                                }
                            }
                            final url = GistUtils.upload(gistToken, gist)

                            final log = new Log(content.readLines())
                            final embed = new EmbedBuilder()
                                .setTitle('Automatic log analysis results', url)
                                .setColor(log.type === LogType.CRASH_LOG ? Color.RED : Color.MAGENTA)
                                .appendDescription("Requested by ${event.user.asMention}.")

                            final BiFunction<Integer, Integer, String> lineNumbers = { Integer ln, Integer ln2 ->
                                "$url#file-log-L${ln + 1}-L${ln2 + 1}"
                            }

                            if (log.type === LogType.CRASH_LOG) {
                                final ex = log.exceptions[0]
                                embed.appendDescription("\n\nCrash reason: [${ex.theException}](${lineNumbers.apply(ex.start, ex.end)})")
                            }

                            final List<ExtraInfo> extraInfo = []
                            final resultHandler = new ResultHandler() {
                                @Override
                                void appendIssue(MessageEmbed em, String title) {
                                    extraInfo.add(new ExtraInfo(title, em))
                                }

                                @Override
                                EmbedBuilder getEmbed() {
                                    return embed
                                }
                            }

                            Log.PARSERS.each {
                                it.handle(log, lineNumbers, resultHandler)
                            }

                            embed.setTimestamp(Instant.now())

                            // U+31U+fe0fU+20e3
                            // U+32U+fe0fU+20e3
                            final List<net.dv8tion.jda.api.interactions.components.buttons.Button> buttons = []
                            if (!extraInfo.isEmpty()) {
                                final List<String> desc = []
                                desc.add('You may use the buttons below for full information about an issue.')
                                extraInfo.eachWithIndex{ ExtraInfo entry, int i ->
                                    final json = entry.embed().toData()
                                    json.put('type', 'rich')
                                    desc.add("${i + 1}. **${entry.title()}**".toString())
                                    buttons.add(components.createButton(ButtonStyle.SECONDARY, com.matyrobbrt.jdahelper.components.Component.Lifespan.TEMPORARY, [
                                            json.toString(), String.valueOf(i + 1), newMsg.jumpUrl
                                    ])
                                        .emoji(Emoji.fromUnicode("U+${31 + i}U+fe0fU+20e3")).build())
                                }
                                embed.addField('Possible issues and fixes', desc.join('\n'), false)
                            }

                            newMsg.editMessage(new MessageEditBuilder()
                                .setEmbeds(embed.build())
                                .setContent(null)
                                .setComponents(buttons.isEmpty() ? [] : JavaCalls.makeButtonsFrom(buttons, Function.identity()))
                                .build()).mentionRepliedUser(false).queue()
                        }}
                    }
                }
            }
        }
    }
}

@CompileStatic
@ImmutableOptions(knownImmutableClasses = [MessageEmbed])
record ExtraInfo(String title, MessageEmbed embed) {

}
