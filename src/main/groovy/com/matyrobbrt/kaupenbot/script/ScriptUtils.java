package com.matyrobbrt.kaupenbot.script;

import com.matyrobbrt.kaupenbot.KaupenBot;
import com.matyrobbrt.kaupenbot.extensions.Extensions;
import com.matyrobbrt.kaupenbot.extensions.misc.ReferencingExtension;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.Nullable;

public class ScriptUtils {
    @Nullable
    public static MessageEmbed reference(String messageLink) {
        return reference(messageLink, null);
    }
    @Nullable
    public static MessageEmbed reference(String messageLink, @Nullable ScriptArgument quoter) {
        final var message = Extensions.getMessageByLink(KaupenBot.getJda(), messageLink);
        if (message == null) return null;
        try {
            return message.submit()
                    .exceptionally(fn -> null)
                    .thenApply(msg -> {
                        if (msg == null) return null;
                        final ReferencingExtension.Quoter quoter1;
                        if (quoter == null) {
                            quoter1 = null;
                        } else {
                            quoter1 = new ReferencingExtension.Quoter(
                                    Long.parseLong(quoter.getProperty("id").toString()),
                                    quoter.getChildrenProperty("user.asTag").toString(),
                                    quoter.getProperty("avatarUrl").toString()
                            );
                        }
                        return ReferencingExtension.reference(msg, quoter1);
                    }).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
