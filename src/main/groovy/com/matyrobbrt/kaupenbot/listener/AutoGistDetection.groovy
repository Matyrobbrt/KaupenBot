package com.matyrobbrt.kaupenbot.listener

import com.matyrobbrt.kaupenbot.util.util.Gist
import com.matyrobbrt.kaupenbot.util.util.GistUtils
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.annotations.NotNull

import javax.annotation.Nonnull

@CompileStatic
class AutoGistDetection extends ListenerAdapter {
    private static final List<String> ACCEPTED_EXTENSIONS = List.of(
            'txt', 'gradle', 'log', 'java', 'txt',
            'kt', 'groovy', 'js', 'json', 'kts'
    )
    private static final UnicodeEmoji EMOJI = Emoji.fromUnicode('🗒️')

    private final String gistToken
    AutoGistDetection(String gistToken) {
        this.gistToken = gistToken
    }

    @Override
    void onMessageReceived(@NotNull @Nonnull MessageReceivedEvent event) {
        if (event.message.attachments.any { it.fileExtension in ACCEPTED_EXTENSIONS }) {
            event.message.addReaction(EMOJI).queue()
        }
    }

    @Override
    void onMessageReactionAdd(@NotNull @Nonnull MessageReactionAddEvent event) {
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
