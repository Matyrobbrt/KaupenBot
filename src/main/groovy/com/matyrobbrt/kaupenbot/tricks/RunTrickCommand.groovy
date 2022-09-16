package com.matyrobbrt.kaupenbot.tricks
import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.DismissListener
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

@CompileStatic
final class RunTrickCommand extends SlashCommand {

    RunTrickCommand() {
        super()
        name = "run"
        help = "Invoke a specific trick by name."

        options = List.of(
                new OptionData(OptionType.STRING, "name", "The name of the trick to run").setRequired(true).setAutoComplete(true),
                new OptionData(OptionType.STRING, "args", "The arguments for the trick, if any").setRequired(false)
        );
    }

    /**
     * Executes the command.
     *
     * @param event the slash command event
     */
    @Override
    protected void execute(final SlashCommandEvent event) {
        final var name = event.getOption("name", "", OptionMapping::getAsString);
        event.deferReply().queue(hook -> {
            Tricks.getTrick(name).ifPresentOrElse(
                    trick -> trick.execute(new SlashCtx(event, hook, event.getOption("args", "", OptionMapping::getAsString).split(" "))),
                    () -> {
                        hook.editOriginal('No trick with that name was found.')
                                .setActionRow(DismissListener.createDismissButton(event)).queue()
                    }
            );
        });
    }

    @Override
    void onAutoComplete(final CommandAutoCompleteInteractionEvent event) {
        final var currentChoice = event.getInteraction().getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        event.replyChoices(getNamesStartingWith(currentChoice, 5)).queue()
    }

    static List<Command.Choice> getNamesStartingWith(final String currentChoice, final int limit) {
        return Tricks.getTricks().stream().filter(t -> t.getNames().get(0).startsWith(currentChoice))
                .limit(limit).map(t -> new Command.Choice(t.getNames().get(0), t.getNames().get(0))).toList();
    }

    static final class Prefix extends com.jagrosh.jdautilities.command.Command {

        private final String trickName;

        Prefix(Trick trick) {
            this.name = trick.getNames().get(0)
            this.trickName = name
            this.aliases = trick.getNames().toArray(String[]::new)
            help = "Invokes the trick " + trickName
        }

        @Override
        protected void execute(final CommandEvent event) {
            Tricks.getTrick(trickName).ifPresentOrElse(trick -> trick.execute(new NormalCtx(event, event.getArgs().split(' '))),
                    () -> event.getMessage().reply('This trick does not exist anymore!').queue())
        }
    }
}
