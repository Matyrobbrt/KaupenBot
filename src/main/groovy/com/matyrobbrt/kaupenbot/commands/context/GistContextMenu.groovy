package com.matyrobbrt.kaupenbot.commands.context

import com.jagrosh.jdautilities.command.CooldownScope
import com.jagrosh.jdautilities.command.MessageContextMenu
import com.jagrosh.jdautilities.command.MessageContextMenuEvent
import com.matyrobbrt.kaupenbot.util.util.Gist
import com.matyrobbrt.kaupenbot.util.util.GistUtils
import groovy.transform.CompileStatic

@CompileStatic
class GistContextMenu extends MessageContextMenu {
    private final String gistToken

    GistContextMenu(String token) {
        name = 'Create Gist'
        guildOnly = true
        gistToken = token
        cooldownScope = CooldownScope.USER
        cooldown = 5
    }

    public static final List<String> BLACKLISTED_ATTACHMENTS = List.of("png", "jpg", "jpeg", "webm", "mp4", "hevc");

    @Override
    protected void execute(MessageContextMenuEvent event) {
        if ((event.target.attachments.isEmpty() || event.target.attachments.stream().allMatch { it.fileExtension in BLACKLISTED_ATTACHMENTS }) && !event.target.contentRaw.contains('```')) {
            event.replyProhibited('The specified message has no (Gist-able) attachments!').queue()
            return
        }
        event.deferReply().queue()
        final message = event.target

        final var hasCodeBlocks = message.contentRaw.contains('```')
        final var gist = hasCodeBlocks ? new Gist('', false) : new Gist(message.contentRaw, false)
        if (hasCodeBlocks) {
            var content = message.getContentRaw().substring(message.getContentRaw().indexOf('```') + 3)
            final var indexOf$ = content.indexOf('\n')
            final var extension = content.substring(0, indexOf$ == -1 ? 0 : indexOf$)
            content = content.substring(content.indexOf('\n') + 1)
            content = content.substring(0, content.lastIndexOf('```'))
            gist.addFile('codeblocks' + (extension.isBlank() ? '' : '.' + extension), content)
        }
        for (final attach : message.getAttachments()) {
            if (attach.fileExtension in BLACKLISTED_ATTACHMENTS) continue
            try (final is = URI.create(attach.proxy.url).toURL().openStream()) {
                gist.addFile(attach.fileName, GistUtils.readInputStream(is))
            }
        }
        final url = GistUtils.upload(gistToken, gist)
        event.hook.editOriginal("Gist created successfully: <$url>").queue()
    }
}
