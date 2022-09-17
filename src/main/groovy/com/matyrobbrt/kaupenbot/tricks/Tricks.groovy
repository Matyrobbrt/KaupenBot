package com.matyrobbrt.kaupenbot.tricks

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.DismissListener
import com.matyrobbrt.jdahelper.components.Component
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.script.ScriptArgument
import com.matyrobbrt.kaupenbot.script.ScriptObjects
import com.matyrobbrt.kaupenbot.script.ScriptRunner
import com.matyrobbrt.kaupenbot.util.PaginatedSlashCommand
import groovy.transform.CompileStatic
import groovy.transform.ImmutableOptions
import groovy.transform.TupleConstructor
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.MarkdownUtil
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.awt.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.List
import java.util.function.Supplier

@CompileStatic
final class Tricks {

    /**
     * The storage location for the tricks file.
     */
    private static final Supplier<Path> TRICK_STORAGE_PATH = () -> Path.of('tricks.json')

    /**
     * The GSON instance.
     */
    public static final Gson GSON = new GsonBuilder().create()

    /**
     * All registered tricks.
     */
    private static @Nullable List<Trick> tricks = null

    /**
     * Gets a trick by name.
     *
     * @param name the name of the trick
     * @return an optional of the trick, or empty if no such trick exists
     */
    static Optional<Trick> getTrick(final String name) {
        return getTricks().stream().filter(trick -> name in trick.names).findAny()
    }

    /**
     * Gets all tricks.
     *
     * @return a list of the tricks
     */
    static List<Trick> getTricks() {
        if (tricks == null) {
            final var path = TRICK_STORAGE_PATH.get()
            if (!Files.exists(path)) {
                return tricks = new ArrayList<>()
            }
            final var typeOfList = new TypeToken<List<Trick>>() {
            }.getType()
            try (final reader = path.newReader()) {
                return tricks = GSON.fromJson(reader, typeOfList)
            } catch (final IOException exception) {
                KaupenBot.log.error('Failed to read tricks file: ', exception)
                tricks = new ArrayList<>()
            }
        }
        return tricks
    }

    /**
     * Adds a trick.
     *
     * @param trick the trick to add.
     */
    static void addTrick(final Trick trick) {
        getTricks().add(trick)
        write()
        KaupenBot.client.addCommand(new RunTrickCommand.Prefix(trick))
    }

    /**
     * Removes a trick.
     *
     * @param trick the trick
     */
    static void removeTrick(final Trick trick) {
        getTricks().remove(trick)
        write()
        KaupenBot.client.removeCommand(trick.getNames().get(0))
    }

    static void replaceTrick(final Trick oldTrick, final Trick newTrick) {
        getTricks().remove(oldTrick)
        getTricks().add(newTrick)
        write()
    }

    /**
     * Write tricks to disk.
     */
    private static void write() {
        final var tricksFile = TRICK_STORAGE_PATH.get().toFile()
        final var tricks = getTricks()
        try (var writer = new OutputStreamWriter(new FileOutputStream(tricksFile), StandardCharsets.UTF_8)) {
            GSON.toJson(tricks, writer)
        } catch (final FileNotFoundException exception) {
            KaupenBot.log.error('A FileNotFoundException occurred saving tricks: ', exception)
        } catch (final IOException exception) {
            KaupenBot.log.error('An IOException occurred saving tricks: ', exception)
        }
    }
}

@CompileStatic
@TupleConstructor
final class Trick {
    final String script
    final List<String> names

    void execute(TrickContext context) {
        try {
            ScriptRunner.run([
                    'guild' : ScriptObjects.guild(context.guild),
                    'user': ScriptObjects.user(context.user, true),
                    'args': context.arguments,
                    'member': context.member === null ? null : ScriptObjects.member(context.member, true),
                    'channel': ScriptObjects.messageChannel(context.channel, true),
                    'context': ScriptArgument.make()
                            .addVoidMethod('reply', 1) {
                                context.reply(ScriptObjects.message(it, 0))
                            }
                            .addVoidMethod('replyEmbed', 1) {
                                context.replyEmbed(ScriptObjects.embed(it, 0))
                            }
                            .addVoidMethod('replyEmbeds', -1) {
                                context.replyEmbeds(it.stream(i -> ScriptObjects.embed(it, i))
                                        .filter { it !== null }.limit(3).toList())
                            }
            ], script)
        } catch (Exception e) {
            context.reply(MessageCreateData.fromContent("There was an exception evaluating the script: $e"))
        }
    }
}

@CompileStatic
interface TrickContext {
    User getUser()
    Member getMember()
    Guild getGuild()
    MessageChannel getChannel()
    String[] getArguments()
    void reply(MessageCreateData data)
    default void replyEmbed(MessageEmbed embed) {
        replyEmbeds(List.of(embed))
    }
    void replyEmbeds(List<MessageEmbed> embeds)
}

@CompileStatic
@ImmutableOptions(knownImmutableClasses = CommandEvent)
record NormalCtx(CommandEvent event, String[] args) implements TrickContext {

    @Nullable
    @Override
    Member getMember() {
        return event.getMember()
    }

    @NotNull
    @Override
    User getUser() {
        return event.getMessage().getAuthor()
    }

    @Override
    Guild getGuild() {
        return event().guild
    }

    @NotNull
    @Override
    MessageChannel getChannel() {
        return event.getChannel()
    }

    @NotNull
    @Override
    String[] getArguments() {
        return args
    }

    @Override
    void reply(MessageCreateData data) {
        event.message.reply(data)
            .setAllowedMentions(ScriptObjects.ALLOWED_MENTIONS)
            .setActionRow(DismissListener.createDismissButton(getUser(), event.message))
            .queue()
    }

    @Override
    void replyEmbeds(final List<MessageEmbed> embeds) {
        event.getMessage().reply(MessageCreateData.fromEmbeds(embeds))
                .setActionRow(DismissListener.createDismissButton(getUser(), event.getMessage()))
                .mentionRepliedUser(false).queue()
    }
}

@CompileStatic
@ImmutableOptions(knownImmutableClasses = [SlashCommandEvent, InteractionHook])
record SlashCtx(SlashCommandEvent event, InteractionHook hook, String[] args) implements TrickContext {

    @Nullable
    @Override
    Member getMember() {
        return event.getMember()
    }

    @NotNull
    @Override
    User getUser() {
        return event.getUser()
    }

    @NotNull
    @Override
    MessageChannel getChannel() {
        return event.getChannel()
    }

    @Override
    String[] getArguments() {
        return args()
    }

    @Override
    Guild getGuild() {
        return event().guild
    }

    @Override
    void replyEmbeds(List<MessageEmbed> embeds) {
        reply(MessageCreateData.fromEmbeds(embeds))
    }

    @Override
    void reply(MessageCreateData data) {
        hook().editOriginal(MessageEditData.fromCreateData(data))
            .setAllowedMentions(ScriptObjects.ALLOWED_MENTIONS)
            .setActionRow(DismissListener.createDismissButton(getUser()))
            .queue()
    }
}

@CompileStatic
final class TrickCommand extends SlashCommand {
    TrickCommand() {
        name = 'trick'
        guildOnly = true
        children = new SlashCommand[] {
            new RunTrickCommand(),
            new RawTrickCommand(),
            new RemoveTrickCommand(),
            new ListTricksCommand()
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    @Override
    void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.subcommandName == 'run') {
            children[0].onAutoComplete(event)
        } else if (event.subcommandName == 'raw') {
            children[1].onAutoComplete(event)
        } else if (event.subcommandName == 'remove') {
            children[2].onAutoComplete(event)
        }
    }
}

@CompileStatic
final class RawTrickCommand extends SlashCommand {
    RawTrickCommand() {
        name = 'raw'
        help = 'Gets the raw representation of the trick'
        guildOnly = true
        options = Collections.singletonList(new OptionData(OptionType.STRING, "trick", "The trick to get.")
                .setRequired(true).setAutoComplete(true))
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final var trickName = event.string('trick')

        Tricks.getTrick(trickName).ifPresentOrElse(trick -> {
            event.replyEmbeds(new EmbedBuilder().setTitle("Raw contents of " + trickName)
                    .setDescription(MarkdownUtil.codeblock('groovy', trick.script)).setColor(Color.GREEN)
                    .addField("Trick Names", String.join(" ", trick.getNames()), false)
                    .setTimestamp(Instant.now()).setFooter("Requested by: " + event.getUser().getAsTag(),
                    event.getUser().getAvatarUrl()).build()).addActionRow(DismissListener.createDismissButton(event)).queue()
        }, () -> event.reply("This trick does not exist anymore!").setEphemeral(true).queue())
    }
}

@CompileStatic
final class ListTricksCommand extends PaginatedSlashCommand {

    ListTricksCommand() {
        super(KaupenBot.paginator('list-tricks-cmd')
            .lifespan(Component.Lifespan.TEMPORARY)
            .itemsPerPage(10)
            .buttonsOwnerOnly(true))
        name = 'list'
        help = 'List all registered tricks.'
        options = List.of(
                new OptionData(OptionType.INTEGER, 'page', 'The index of the page to display. 1 if not specified.')
        )
        guildOnly = true
    }

    /**
     * Execute.
     *
     * @param event the event
     */
    @Override
    protected void execute(final SlashCommandEvent event) {
        final var pgIndex = event.getOption('page', 1, OptionMapping::getAsInt)
        final var startingIndex = (pgIndex - 1) * getItemsPerPage()
        final var maximum = Tricks.getTricks().size()
        if (maximum <= startingIndex) {
            event.deferReply().setContent("The page index provided ($pgIndex) was too big! There are only ${getPagesNumber(maximum)} pages.").queue()
            return
        }

        createPaginatedMessage(event, startingIndex, maximum).queue()
    }

    @Override
    protected EmbedBuilder getEmbed(final int from, final int maximum, final List<String> arguments) {
        return new EmbedBuilder()
                .setTitle("Tricks page ${getPageNumber(from)}/${getPagesNumber(maximum)}")
                .setDescription(Tricks.getTricks()
                        .subList(from, Math.min(from + getItemsPerPage(), maximum))
                        .stream()
                        .map(it -> it.getNames().stream().reduce("", (a, b) -> (a.isEmpty() ? a : a + " / ") + b))
                        .reduce("", (a, b) -> a + "\n" + b))
                .setTimestamp(Instant.now())
    }
}

@CompileStatic
final class RemoveTrickCommand extends SlashCommand {

    /**
     * Instantiates a new Cmd remove trick.
     */
    RemoveTrickCommand() {
        name = 'remove'
        help = 'Removes a trick'
        guildOnly = true
        options = Collections.singletonList(new OptionData(OptionType.STRING, "trick", "The trick to delete.", true))
        userPermissions = new Permission[] {
            Permission.MODERATE_MEMBERS
        }
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        final var name = event.string('trick')

        Tricks.getTrick(name).ifPresentOrElse(trick -> {
            Tricks.removeTrick(trick)
            event.reply("Removed trick!").setEphemeral(false).queue()
        }, () -> event.deferReply(true).setContent("Unknown trick: $name").queue())
    }

    @Override
    void onAutoComplete(final CommandAutoCompleteInteractionEvent event) {
        final var currentChoice = event.getInteraction().getFocusedOption().getValue().toLowerCase(Locale.ROOT)
        event.replyChoices(RunTrickCommand.getNamesStartingWith(currentChoice, 5)).queue()
    }
}