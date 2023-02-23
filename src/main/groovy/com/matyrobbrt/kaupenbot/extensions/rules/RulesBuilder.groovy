package com.matyrobbrt.kaupenbot.extensions.rules

import groovy.transform.CompileStatic
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData

import java.awt.*
import java.util.List

@CompileStatic
class RulesBuilder {
    private final List<MessageCreateBuilder> messages = new ArrayList<>()

    void appendEmbed(MessageEmbed embed) {
        if (messages.isEmpty()) {
            messages.add(new MessageCreateBuilder().addEmbeds(embed))
        } else {
            final last = messages.last()
            if (last.content.isBlank()) {
                final embeds = last.embeds
                if (embeds.size() >= Message.MAX_EMBED_COUNT) {
                    messages.add(new MessageCreateBuilder().addEmbeds(embed))
                } else {
                    last.addEmbeds(embed)
                }
            } else {
                messages.add(new MessageCreateBuilder().addEmbeds(embed))
            }
        }
    }

    void append(String message) {
        if (messages.isEmpty()) {
            messages.add(new MessageCreateBuilder().setContent(message))
        } else {
            final last = messages.last()
            if (last.embeds.isEmpty()) {
                last.addContent("\n$message")
            } else {
                messages.add(new MessageCreateBuilder().setContent(message))
            }
        }
    }

    void appendNew(String message) {
        messages.add(new MessageCreateBuilder().setContent(message))
    }

    private int ruleIndex = 1
    void appendRule(String ruleTitle, String ruleDesc, int ruleColour = createRandomBrightColor().getRGB()) {
        appendEmbed(embed {
            title = "${ruleIndex++}. $ruleTitle"
            description = ruleDesc
            color = ruleColour
        })
    }

    private static final float MIN_BRIGHTNESS = 0.8f
    private static final Random RANDOM = new Random()
    static Color createRandomBrightColor() {
        float h = RANDOM.nextFloat()
        float s = RANDOM.nextFloat()
        float b = (float) (MIN_BRIGHTNESS + ((1f - MIN_BRIGHTNESS) * RANDOM.nextFloat()))
        return Color.getHSBColor(h, s, b)
    }

    List<MessageCreateData> build() {
        return this.messages*.build()
    }
}
