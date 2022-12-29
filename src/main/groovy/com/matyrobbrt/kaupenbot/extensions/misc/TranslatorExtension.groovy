package com.matyrobbrt.kaupenbot.extensions.misc

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.jagrosh.jdautilities.command.CommandClient
import com.jagrosh.jdautilities.command.MessageContextMenu
import com.jagrosh.jdautilities.command.MessageContextMenuEvent
import com.matyrobbrt.bingtranslate.Language
import com.matyrobbrt.bingtranslate.TranslationResult.Translation
import com.matyrobbrt.bingtranslate.Translator
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.common.util.JavaCalls
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.requests.RestAction
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Supplier

import static com.matyrobbrt.bingtranslate.Language.*

@CompileStatic
@RegisterExtension(botId = 'kbot', value = 'translator')
class TranslatorExtension implements BotExtension {
    private final TranslationMenu menu = new TranslationMenu()

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        client.addContextMenu(menu)

        manager.addCommand {
            name = 'translate'
            description = 'Translate text from a language to another.'
            options = [
                    new OptionData(OptionType.STRING, 'text', 'The text to translate', true).setMaxLength(Translator.MAX_TEXT_LENGTH),
                    new OptionData(OptionType.STRING, 'to', 'The language to translate to', true).setAutoComplete(true),
                    new OptionData(OptionType.STRING, 'from', 'The language to translate from. Do not provide to auto-detect the language').setAutoComplete(true)
            ]
            autoCompleteOptions(['to', 'from']) { current ->
                current = current.toLowerCase(Locale.ROOT)
                replyChoices(Arrays.stream(values())
                    .filter { it.name.toLowerCase(Locale.ROOT).startsWith(current) }
                    .limit(25)
                    .map { new Command.Choice(it.name, it.code) }.toList()).queue()
            }

            action {
                final Language to = byCode(string('to'))
                if (to === null) {
                    replyProhibited("Unknown target language ${string('to')}!").queue(); return
                }

                final Language from = it.getOption('from')?.asString?.with { byCode(it) }

                deferReply(true).flatMap {
                    try {
                        final result = Translator.translate(string('text'), from, to)
                        if (result.response() === null) {
                            return it.editOriginal("Server returned non-200 status code: ${result.response()}")
                        } else {
                            final Translation translation = result.result().translations()[0]
                            return it.editOriginal("${result.result().detectedLanguage().language().display} ---> ${translation.to().display}:\n${translation.text()}")
                        }
                    } catch (Exception exception) {
                        return it.editOriginal("Encountered exception translating: $exception")
                    }
                }.queue()
            }
        }
    }

    @Override
    void subscribeEvents(JDA jda) {
        jda.addEventListener(menu)
    }
}

@PackageScope(PackageScopeTarget.CLASS) @CompileStatic
final class TranslationMenu extends MessageContextMenu implements EventListener {
    private static final List<Language> LANGUAGES = [
            ENGLISH, SPANISH, GERMAN, FRENCH, ITALIAN,
            ROMANIAN, PORTUGUESE_BRAZIL, UKRAINIAN, RUSSIAN, CHINESE_TRADITIONAL,
            ARABIC, HINDI, JAPANESE, WELSH, DUTCH,
            GREEK, POLISH, SWEDISH, INDONESIAN, VIETNAMESE
    ]
    private static final String BUTTON_PREFIX = 'translate_'

    private final Cache<String, Supplier<RestAction>> deletor = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(2, TimeUnit.MINUTES)
        .build()
    TranslationMenu() {
        this.name = 'Translate'
    }

    @Override
    protected void execute(MessageContextMenuEvent event) {
        if (event.target.contentDisplay.length() > Translator.MAX_TEXT_LENGTH) {
            event.replyEphemeral("Cannot translate messages longer than $Translator.MAX_TEXT_LENGTH characters!").queue()
            return
        }

        event.replyEphemeral("Please select the language to translate to.")
            .setComponents(JavaCalls.makeButtonsFrom(LANGUAGES, {
                Button.of(ButtonStyle.SECONDARY, BUTTON_PREFIX + it.code + '/' + event.target.id, it.getName(), it.asEmoji())
            }))
            .queue { hook ->
                deletor.put(event.target.id, { hook.deleteOriginal() } as Supplier)
            }
    }

    @Override
    void onEvent(@Nonnull GenericEvent gevent) {
        if (!(gevent instanceof ButtonInteractionEvent)) return
        final event = (ButtonInteractionEvent) gevent
        if (!event.button.id?.startsWith(BUTTON_PREFIX)) return

        final split = event.button.id.replace(BUTTON_PREFIX, '').split('/')
        final langCode = split[0]
        final deletor = this.deletor.getIfPresent(split[1])
        event.deferReply(true).flatMap { event.channel.retrieveMessageById(split[1]) }.flatMap { message ->
            if (message.contentDisplay.length() > Translator.MAX_TEXT_LENGTH) {
                return event.hook.editOriginal("Cannot translate messages longer than $Translator.MAX_TEXT_LENGTH characters!")
            }

            try {
                final result = Translator.translate(message.contentDisplay, null, byCode(langCode))
                if (result.response() === null) {
                    return event.hook.editOriginal("Server returned non-200 status code: ${result.response()}")
                } else {
                    final Translation translation = result.result().translations()[0]
                    return event.hook.editOriginal("${result.result().detectedLanguage().language().display} ---> ${translation.to().display}:\n${translation.text()}")
                }
            } catch (Exception exception) {
                return event.hook.editOriginal("Encountered exception translating: $exception")
            }
        }.flatMap({ deletor !== null }) { deletor.get() }.queue()
    }
}