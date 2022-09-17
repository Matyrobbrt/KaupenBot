package com.matyrobbrt.kaupenbot.commands.extensions

import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.commands.api.BotExtension
import com.matyrobbrt.kaupenbot.commands.api.CommandManager
import com.matyrobbrt.kaupenbot.commands.api.RegisterExtension
import groovy.transform.CompileStatic
import kotlin.Pair
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction

import java.awt.*

@CompileStatic
@RegisterExtension
final class SuggestionExtension implements BotExtension {
    private static final Emoji UPVOTE = Emoji.fromUnicode('⬆')
    private static final Emoji DOWN_VOTE = Emoji.fromUnicode('⬇')
    private static final Emoji APPROVE = Emoji.fromUnicode('✔')
    private static final Emoji DENY = Emoji.fromUnicode('❌')

    private static final String APPROVE_ID = 'approve'
    private static final String DENY_ID = 'deny'

    @Override
    void fillCommands(CommandManager manager) {
        manager.addCommand {
            name = 'suggest'
            guildOnly = true
            description = 'Provide a suggestion.'
            options = [
                    new OptionData(OptionType.CHANNEL, 'channel', 'The channel to send the suggestion in.', true),
                    new OptionData(OptionType.STRING, 'suggestion', 'The suggestion.', true)
            ]
            checkIf({ KaupenBot.config.channels.suggestionChannels.contains(it.getOption('channel')?.asChannel?.idLong) }, 'Provided channel is not a suggestions channel.')
            action { event ->
                final channel = getOption('channel')?.asChannel
                channel.asGuildMessageChannel().sendMessageEmbeds(embed {
                    sentNow()
                    color = Color.LIGHT_GRAY
                    title = 'New suggestion'
                    description = string('suggestion')
                    setFooter("Suggested by $user.asTag", member.effectiveAvatarUrl)
                }).addActionRow(
                        net.dv8tion.jda.api.interactions.components.buttons.Button.primary(APPROVE_ID, APPROVE),
                        net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(DENY_ID, DENY)
                ).flatMap {
                    addReaction(it, UPVOTE).flatMap { addReaction(it, DOWN_VOTE) }
                }.flatMap { msg ->
                    reply("Suggestion added! [Jump to suggestion.]($msg.jumpUrl)").setEphemeral(true)
                        .flatMap {msg.createThreadChannel("Discussion of ${event.member.effectiveName}’s suggestion") }
                }.queue()
            }
        }
    }

    @Override
    void subscribeEvents(JDA jda) {
        jda.subscribe(ButtonInteractionEvent) {
            if (it.button.id != APPROVE_ID || !it.fromGuild) return
            if (!it.member.hasPermission(Permission.MESSAGE_MANAGE)) {
                replyProhibited('You cannot approve suggestions!')
                return
            }
            replyModal(Modal.create(APPROVE_ID + '/' + messageId, 'Approve suggestion')
                .addActionRow(TextInput.create(
                        'reason', 'Approval Reason', TextInputStyle.PARAGRAPH
                ).setRequired(false).build())
                .build()).queue()
        }
        jda.subscribe(ButtonInteractionEvent) {
            if (it.button.id != DENY_ID || !it.fromGuild) return
            if (!it.member.hasPermission(Permission.MESSAGE_MANAGE)) {
                replyProhibited('You cannot deny suggestions!')
                return
            }
            replyModal(Modal.create(DENY_ID + '/' + messageId, 'Deny suggestion')
                .addActionRow(TextInput.create(
                        'reason', 'Denial Reason', TextInputStyle.PARAGRAPH
                ).setRequired(false).build())
                .build()).queue()
        }

        jda.subscribe(ModalInteractionEvent) { modal ->
            final id = modalId.split('/')
            if (id[0] != APPROVE_ID) return
            deferReply(true).queue()
            channel.retrieveMessageById(id[1])
                .flatMap {
                    final embed = new EmbedBuilder(it.embeds[0])
                    countVotes(it, embed)
                    embed.addField('Suggestion Approved', (getValue('reason')?.asString ?: '*No reason given*') + "\nApproved by: $member.asMention ($member.id)", true)
                    embed.color = Color.GREEN
                    it.editMessageEmbeds(embed.build()).setComponents()
                }.flatMap { it.clearReactions() }
                .flatMap { modal.hook.editOriginal('Suggestion approved successfully!') }
                .queue()
        }
        jda.subscribe(ModalInteractionEvent) { modal ->
            final id = modalId.split('/')
            if (id[0] != DENY_ID) return
            deferReply(true).queue()
            channel.retrieveMessageById(id[1])
                .flatMap {
                    final embed = new EmbedBuilder(it.embeds[0])
                    countVotes(it, embed)
                    embed.addField('Suggestion Denied', (getValue('reason')?.asString ?: '*No reason given*') + "\nDenied by: $member.asMention ($member.id)", true)
                    embed.color = Color.RED
                    it.editMessageEmbeds(embed.build()).setComponents()
                }.flatMap { it.clearReactions() }
                .flatMap { modal.hook.editOriginal('Suggestion denied successfully!') }
                .queue()
        }

        jda.subscribe(MessageReactionAddEvent) { event ->
            if (event.user.bot || channel.idLong !in KaupenBot.config.channels.suggestionChannels) return
            final boolean downvote = reaction.emoji == DOWN_VOTE
            final boolean upvote = reaction.emoji == UPVOTE
            if (!downvote && !upvote) return
            final oppositeEmoji = downvote ? UPVOTE : DOWN_VOTE
            retrieveMessage().flatMap({ it.getReaction(oppositeEmoji) !== null }) { msg ->
                msg.getReaction(oppositeEmoji).retrieveUsers().map { new Pair<>(it, msg) }
            }.queue({
                if (event.user in it.component1()) {
                    event.reaction.removeReaction(event.user).flatMap {
                        event.user.openPrivateChannel()
                    }.flatMap { _ ->
                        _.sendMessage("You cannot upvote and downvote the same suggestion! Please decide on only one option. Suggestion: ${it.component2().jumpUrl}")
                    }.queue()
                }
            }, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
        }
    }

    private static void countVotes(final Message message, final EmbedBuilder embed) {
        final upvotes = message.getReaction(UPVOTE).count - 1
        final downvotes = message.getReaction(DOWN_VOTE).count - 1

        if (upvotes > 0)
            embed.appendDescription("\n\n**Upvotes**: $upvotes")
        if (downvotes > 0)
            embed.appendDescription("\n**Downvotes**: $downvotes")
    }

    private static RestAction<Message> addReaction(Message message, Emoji emoji) {
        message.addReaction(emoji).map { message }
    }
}
