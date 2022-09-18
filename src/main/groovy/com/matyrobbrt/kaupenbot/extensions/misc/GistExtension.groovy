package com.matyrobbrt.kaupenbot.extensions.misc

import com.jagrosh.jdautilities.command.CommandClient
import com.matyrobbrt.kaupenbot.commands.context.GistContextMenu
import com.matyrobbrt.kaupenbot.common.command.CommandManager
import com.matyrobbrt.kaupenbot.common.extension.BotExtension
import com.matyrobbrt.kaupenbot.common.extension.ExtensionArgument
import com.matyrobbrt.kaupenbot.common.extension.RegisterExtension
import com.matyrobbrt.kaupenbot.util.util.Gist
import com.matyrobbrt.kaupenbot.util.util.GistUtils
import groovy.transform.CompileStatic
import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent

@CompileStatic
@RegisterExtension(botId = 'kbot')
final class GistExtension implements BotExtension {
    private static final List<String> ACCEPTED_EXTENSIONS = List.of(
            'txt', 'gradle', 'log', 'java', 'txt',
            'kt', 'groovy', 'js', 'json', 'kts'
    )
    private static final UnicodeEmoji EMOJI = Emoji.fromUnicode('🗒️')

    private final String gistToken
    GistExtension(@ExtensionArgument('env') Dotenv env) {
        this.gistToken = env.get('GIST_TOKEN')
    }

    @Override
    void subscribeEvents(JDA jda) {
        if (gistToken === null) return
        jda.subscribe(MessageReceivedEvent) { event ->
            if (event.message.attachments.any { it.fileExtension in ACCEPTED_EXTENSIONS }) {
                event.message.addReaction(EMOJI).queue()
            }
        }

        jda.subscribe(MessageReactionAddEvent) { event ->
            if (event.emoji == EMOJI && event.userIdLong !== event.getJDA().selfUser.idLong) {
                event.retrieveMessage().queue {
                    if (it.getReaction(EMOJI).self && it.attachments.any { it.fileExtension in ACCEPTED_EXTENSIONS }) {
                        final gist = new Gist(it.contentRaw, false)
                        for (final attach : it.attachments) {
                            if (attach.fileExtension in ACCEPTED_EXTENSIONS) {
                                try (final is = URI.create(attach.proxy.url).toURL().openStream()) {
                                    gist.addFile(attach.fileName, GistUtils.readInputStream(is))
                                }
                            }
                        }
                        final url = GistUtils.upload(gistToken, gist)
                        it.reply("Created Gist at the request of <@$event.userId>: <$url>")
                                .setAllowedMentions(List.of())
                                .flatMap { event.reaction.clearReactions() }
                                .queue()
                    }
                }
            }
        }
    }

    @Override
    void registerCommands(CommandManager manager, CommandClient client) {
        if (gistToken === null) return
        client.addContextMenu(new GistContextMenu(gistToken))
    }
}
