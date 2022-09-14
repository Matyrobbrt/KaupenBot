package com.matyrobbrt.kaupenbot.commands

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.components.ComponentListener
import com.matyrobbrt.jdahelper.components.context.ButtonInteractionContext
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.api.plugins.WarningsPlugin
import com.matyrobbrt.kaupenbot.api.util.Warning
import com.matyrobbrt.kaupenbot.db.WarningMapper
import com.matyrobbrt.kaupenbot.db.WarningsDAO
import com.matyrobbrt.kaupenbot.util.CallbackCommand
import com.matyrobbrt.kaupenbot.util.PaginatedSlashCommand
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.TimeFormat
import org.jetbrains.annotations.Nullable

import java.awt.*
import java.util.List
import java.util.function.Function

import static com.matyrobbrt.kaupenbot.commands.WarningCommand.REQUIRED_PERMISSIONS
import static com.matyrobbrt.kaupenbot.commands.WarningCommands.*

@CompileStatic
final class WarningCommand extends SlashCommand {

    public static final Permission[] REQUIRED_PERMISSIONS = new Permission[]{
            Permission.MODERATE_MEMBERS
    }

    public static final ComponentListener WARN_ACTION_LISTENER = KaupenBot.getComponentListener('warn-action')
        .onButtonInteraction(WarningCommands::onButton)
        .build()

    WarningCommand() {
        super()
        name = 'warning'
        category = new Category('Moderation')
        guildOnly = true
        userPermissions = REQUIRED_PERMISSIONS

        children = new SlashCommand[]{
                new AddWarn(), new ListWarns(), new ClearWarn()
        }
    }

    @Override
    protected void execute(final SlashCommandEvent event) {

    }
}

@CompileStatic
final class WarnCommand extends Command {

    WarnCommand() {
        name = 'warn'
        category = new Category('Moderation')
        userPermissions = REQUIRED_PERMISSIONS
        guildOnly = true
        help = 'Warn an user.'
    }

    @Override
    protected void execute(CommandEvent event) {
        final split = event.args.split(' ')
        final toWarn = event.message.mentions.users.isEmpty() ?
                event.getJDA().retrieveUserById(split[0])
                .submit(false).get() :
                event.message.mentions.users[0]
        final reason = split.drop(1).join(' ')

        if (toWarn.getIdLong() == event.getMember().getIdLong()) {
            event.message.reply('You cannot interact with yourself!').mentionRepliedUser(false).queue()
            return
        }

        if (!event.getMember().canInteract(event.guild.retrieveMember(toWarn).submit(false).get())) {
            event.message.reply('You do not have permission to warn this user!').mentionRepliedUser(false)
                    .queue()
            return
        }

        final warnId = withExtension { WarningMapper.insert(it, toWarn.idLong, event.guild.idLong, reason, event.member.idLong) }
        final log = { boolean didDm ->
            logWarning(event.guild, toWarn, warnId, event.member, didDm)

            String message = "⚠ Warned `${toWarn.asTag}`.\n**Reason**: $reason"

            if (!didDm) message += '\n*User could not be messaged.*'

            final qu = event.message.reply(message)
            final button = createActionButton(event.guild.idLong, toWarn.idLong, warnId)
            if (button !== null) {
                qu.addActionRow(button)
            }
            qu.queue()
        }
        toWarn.openPrivateChannel()
                .flatMap {
                    it.sendMessageEmbeds(embed {
                        sentNow()
                        color = Color.RED
                        description = "You have been warned in ${event.guild.name}!"
                        addField('Reason', reason, false)
                        setFooter(event.guild.name, event.guild.iconUrl)
                    })
                }.queue({ log(true) }, new ErrorHandler()
                    .handle(ErrorResponse.CANNOT_SEND_TO_USER, { log(false) }))
    }
}

@CompileStatic
@Newify(OptionData)
@PackageScope(PackageScopeTarget.CLASS)
final class AddWarn extends SlashCommand implements CallbackCommand {

    AddWarn() {
        name = "add"
        help = "Adds a new warning to the user"
        options = List.of(
                OptionData(OptionType.USER, 'user', 'The user to warn', true),
                OptionData(OptionType.STRING, "reason", 'The reason of the warning', true)
        )
        userPermissions = REQUIRED_PERMISSIONS

        setCallback {
            final reason = string('reason')
            final userToWarn = user('user')

            if (!canInteract(it, guild[userToWarn])) {
                return
            }
            deferReply().queue()

            final warnId = withExtension { WarningMapper.insert(it, userToWarn.idLong, guild.idLong, reason, member.idLong) }
            final log = { boolean didDm ->
                logWarning(guild, userToWarn, warnId, member, didDm)

                String message = "⚠ Warned `${userToWarn.asTag}`.\n**Reason**: $reason"

                if (!didDm) message += '\n*User could not be messaged.*'

                final button = createActionButton(guild.idLong, userToWarn.idLong, warnId)
                final qu = hook.sendMessage(message)
                if (button != null) qu.addActionRow(button)
                qu.queue()
            }
            userToWarn.openPrivateChannel()
                    .flatMap {
                        it.sendMessageEmbeds(embed {
                            sentNow()
                            color = Color.RED
                            description = "You have been warned in ${guild.name}!"
                            addField('Reason', reason, false)
                            setFooter(guild.name, guild.iconUrl)
                        })
                    }.queue({ log(true) }, new ErrorHandler()
                    .handle(ErrorResponse.CANNOT_SEND_TO_USER, { log(false) }))
        }
    }
}

@CompileStatic
@PackageScope(PackageScopeTarget.CLASS)
final class ListWarns extends PaginatedSlashCommand implements CallbackCommand {

    ListWarns() {
        super(KaupenBot.paginator('list-warns-cmd')
                .buttonsOwnerOnly(true).itemsPerPage(10))
        name = 'list'
        help = 'Lists the warnings of a user.'
        options = List.of(new OptionData(OptionType.USER, 'user', 'The user whose warnings to see.', true))
        userPermissions = REQUIRED_PERMISSIONS
        guildOnly = true

        setCallback {
            final userToSee = user('user')
            final warns = withExtension { it.getWarningsForUser(userToSee.idLong, guild.idLong) }
            createPaginatedMessage(it, warns.size(), userToSee.id, guild.id).queue()
        }
    }

    @Override
    protected EmbedBuilder getEmbed(final int startingIndex, final int maximum, final List<String> arguments) {
        final userID = Long.parseLong(arguments.get(0))
        final warnings = withExtension { it.getWarningsForUser(userID, Long.parseLong(arguments.get(1))) }

        return new EmbedBuilder().tap {
            sentNow()
            color = Color.MAGENTA
            description = "The warnings of ${mentionAndID(userID)}:"
            for (var i = startingIndex; i < Math.min(startingIndex + paginator.getItemsPerPage(), maximum); i++) {
                final var warn = warnings.get(i)
                addField("Warning $warn.warnId:",
                        "Reason: **$warn.reason** | Moderator: ${mentionAndID(warn.moderatorId)} | Timestamp: ${TimeFormat.DATE_TIME_LONG.format(warn.timestamp)}",
                        false)
            }
        }
    }
}

@CompileStatic
@Newify(OptionData)
@PackageScope(PackageScopeTarget.CLASS)
final class ClearWarn extends SlashCommand implements CallbackCommand {

    ClearWarn() {
        name = 'clear'
        help = 'Clears a warning from the user'
        options = List.of(
                OptionData(OptionType.USER, 'user', 'The user to remove the warn from', true),
                OptionData(OptionType.STRING, 'id', 'The ID of the warn to remove. Do not provide it if you want to clean all warnings of that user.')
        )
        userPermissions = REQUIRED_PERMISSIONS

        setCallback {
            final userToWarn = user('user')
            final warnId = string('id')

            if (!canInteract(it, guild[userToWarn])) {
                return
            }

            if (warnId.isBlank()) {
                withExtension { it.clearAll(userToWarn.idLong, guild.idLong) }
                logClear(guild, userToWarn, null, member)
                reply("Sucessfully cleared all `$userToWarn.asTag`'s warnings!").queue()
            } else {
                final var warnExists = withExtension { it.getReasonOptional(warnId).isPresent() }
                if (!warnExists) {
                    reply('A warning with the specified ID could not be found').setEphemeral(true).queue()
                    return
                }

                final doc = withExtension { it.getWarning(warnId) }
                withExtension { it.deleteById(warnId) }
                logClear(guild, userToWarn, doc, member)
                reply("Sucessfully cleared `$userToWarn.asTag`'s warning!").queue()
            }
        }
    }

}

static boolean canInteract(final SlashCommandEvent event, final Member target) {
    if (target == null) {
        event.deferReply(true).setContent('Unknown user!').queue()
        return false
    }

    if (target.getIdLong() == event.getMember().getIdLong()) {
        event.deferReply(true).setContent('You cannot interact with yourself!').mentionRepliedUser(false).queue()
        return false
    }

    if (!event.getMember().canInteract(target)) {
        event.deferReply(true).setContent('You do not have permission to warn this user!').mentionRepliedUser(false)
                .queue()
        return false
    }

    return true
}

static void logWarning(Guild guild, User warnedUser, UUID warnId, Member moderator, boolean managedToDM) {
    final warn = withExtension { it.getWarning(warnId.toString()) }
    KaupenBot.jda.getChannelById(MessageChannel, KaupenBot.config.loggingChannels.moderationLogs)
            .sendMessageEmbeds(embed {
                sentNow()
                color = Color.RED
                title = 'New warning'
                description = "${mentionAndID(warnedUser.idLong)} has been warned by ${mentionAndID(moderator.idLong)}."
                addField('Reason', warn.reason, false)
                addField('Warning ID', warnId.toString(), false)
                setAuthor(moderator.effectiveName, null, moderator.effectiveAvatarUrl)
                setFooter("User ID: $warnedUser.id", warnedUser.effectiveAvatarUrl)
                if (!managedToDM) {
                    appendDescription('\n*User could not be messages.*')
                }
            }).queue()

}
/**
 * @param warning if {@code null}, all warnings were cleared
 */
static void logClear(Guild guild, User warnedUser, @Nullable Warning warning, Member moderator) {
    final channel = KaupenBot.jda.getChannelById(MessageChannel, KaupenBot.config.loggingChannels.moderationLogs)
    if (warning === null) {
        channel.sendMessageEmbeds(embed {
            sentNow()
            color = Color.GREEN
            title = 'Warnings cleared'
            thumbnail = warnedUser.effectiveAvatarUrl
            description = "All the warnings of ${mentionAndID(warnedUser.idLong)} have been cleared!"
            setFooter("Moderator ID: $moderator.id", moderator.effectiveAvatarUrl)
        }).queue()
    } else {
        channel.sendMessageEmbeds(embed {
            sentNow()
            color = Color.GREEN
            title = 'Warning cleared'
            description = "One of the warnings of ${mentionAndID(warnedUser.idLong)} has been removed!"
            thumbnail = warnedUser.effectiveAvatarUrl
            addField('Old reason:', warning.reason, false)
            addField('Old moderator:', mentionAndID(warning.moderatorId), false)
            setFooter("Moderator ID: $moderator.id", moderator.effectiveAvatarUrl)
        }).queue()
    }
}

@Nullable
static net.dv8tion.jda.api.interactions.components.buttons.Button createActionButton(final long guildId, final long userId, final UUID warnId) {
    final warnCount = withExtension { it.getWarningsForUser(userId, guildId) }
    final action = KaupenBot.plugins[WarningsPlugin].getAction(warnCount.size())
    if (action === null) return null
    return WarningCommand.WARN_ACTION_LISTENER.createButton(
            ButtonStyle.SECONDARY, action.type.asButtonLabel(action.duration), null, com.matyrobbrt.jdahelper.components.Component.Lifespan.TEMPORARY,
            [warnId.toString(), userId.toString(), warnCount.size().toString()]
    )
}

static void onButton(final ButtonInteractionContext context) {
    if (!context.member.hasPermission(Permission.MODERATE_MEMBERS)) {
        context.replyProhibited('You cannot use this button!').queue()
        return
    }

    final warnId = context.arguments[0]
    final userId = context.arguments[1]
    final warningNumber = Integer.parseInt(context.arguments[2])

    final action = KaupenBot.plugins[WarningsPlugin].getAction(warningNumber)
    if (action === null) {
        context.replyProhibited("There no longer is a punishment configured for reaching $warningNumber warnings.").queue()
        return
    }

    context.guild.retrieveMember(UserSnowflake.fromId(userId))
        .flatMap { action.type.apply(it, action.duration)
            .reason(action.getReason(
                    withExtension { it.getWarning(warnId) },
                    "Reached warning number $warningNumber: $warnId"
            )) }
        .flatMap {
            context.event.reply('Applied punishment: ' + action.type.asButtonLabel(action.duration)).queue()
        }
        .queue {
            context.event.message.disableButtons()
        }
}

static <R> R withExtension(Function<WarningsDAO, R> callback) {
     KaupenBot.database.withExtension(WarningsDAO, callback::apply)
}

static String mentionAndID(final long id) {
    "<@$id> ($id)"
}