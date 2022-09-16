package com.matyrobbrt.kaupenbot.commands


import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommand
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.matyrobbrt.jdahelper.DismissListener
import com.matyrobbrt.kaupenbot.script.ScriptArgument
import com.matyrobbrt.kaupenbot.script.ScriptObjects
import com.matyrobbrt.kaupenbot.script.ScriptRunner
import com.matyrobbrt.kaupenbot.util.Constants
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import org.jetbrains.annotations.NotNull

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@CompileStatic
class EvalCommand extends SlashCommand {
    private static final ExecutorService EVALUATION_EXECUTOR = {
        final var group = new ThreadGroup("ScriptingEvaluation")
        final var ex = (ThreadPoolExecutor) Executors.newFixedThreadPool(2, r -> new Thread(group,
                r, "ScriptingEvaluator #" + group.activeCount()).tap { it.daemon = true })
        ex.setKeepAliveTime(10, TimeUnit.MINUTES)
        ex.allowCoreThreadTimeOut(true)
        return ex
    }.call()

    EvalCommand() {
        name = 'evaluate'
        help = 'Evaluate a script.'
        aliases = new String[]{'eval'}
        options.add(new OptionData(OptionType.STRING, 'script', 'The script to evaluate'))
        userPermissions = new Permission[] {
            Permission.MODERATE_MEMBERS
        }
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        final var scriptOption = event.getOption("script")
        if (scriptOption != null) {
            event.deferReply().setAllowedMentions(ScriptObjects.ALLOWED_MENTIONS)
                    .queue(hook -> {
                        final var context = createInteractionContext(hook)

                        final var future = EVALUATION_EXECUTOR.submit(() -> {
                            try {
                                ScriptRunner.run(context, scriptOption.asString)
                            } catch (Exception exception) {
                                hook.editOriginal("There was an exception evaluating script: "
                                        + exception.getLocalizedMessage())
                                        .setActionRow(DismissListener.createDismissButton())
                                        .queue()
                            }
                        })
                        Constants.getEXECUTOR().schedule(() -> {
                            if (!future.isDone()) {
                                future.cancel(true)
                                hook.editOriginal('Evaluation was timed out!').setActionRow(DismissListener.createDismissButton()).queue()
                            }
                        }, 4, TimeUnit.SECONDS);
                    });
        } else {
            final var scriptInput = TextInput.create('script', 'Script', TextInputStyle.PARAGRAPH)
                    .setRequired(true)
                    .setPlaceholder('The script to evaluate.')
                    .setRequiredRange(1, TextInput.MAX_VALUE_LENGTH)
                    .build()
            final var modal = Modal.create('evaluate', 'Evaluate a script')
                    .addActionRow(scriptInput)
                    .build()
            event.replyModal(modal).queue()
        }
    }

    static final class ModalListener extends ListenerAdapter {

        @Override
        void onModalInteraction(@NotNull final ModalInteractionEvent event) {
            if (event.getModalId() == 'evaluate') {
                event.deferReply().setAllowedMentions(ScriptObjects.ALLOWED_MENTIONS).queue(hook -> {
                    final var context = createInteractionContext(hook)

                    final var future = EVALUATION_EXECUTOR.submit(() -> {
                        try {
                            ScriptRunner.run(context, (Objects.requireNonNull(event.getValue("script")).asString))
                        } catch (Exception exception) {
                            hook.editOriginal("There was an exception evaluating script: "
                                    + exception.getLocalizedMessage())
                                    .setActionRow(DismissListener.createDismissButton(event))
                                    .queue();
                        }
                    });
                    Constants.EXECUTOR.schedule(() -> {
                        if (!future.isDone()) {
                            future.cancel(true);
                            hook.editOriginal("Evaluation was timed out!")
                                    .setActionRow(DismissListener.createDismissButton(event)).queue();
                        }
                    }, 4, TimeUnit.SECONDS);
                });
            }
        }
    }

    @Override
    protected void execute(final CommandEvent event) {
        var script = event.getArgs();
        if (script.contains('```groovy') && script.endsWith('```')) {
            script = script.substring(script.indexOf('```groovy') + 9)
            script = script.substring(0, script.lastIndexOf('```'))
        }
        if (!event.getMessage().getAttachments().isEmpty()) {
            for (final attach : event.getMessage().getAttachments()) {
                if (attach.fileExtension == 'groovy') {
                    try (final is = URI.create(attach.proxyUrl).toURL().openStream()) {
                        script = is.readAllBytes()
                        break
                    }
                }
            }
        }
        final String finalScript = script
        final context = [
                'guild': ScriptObjects.guild(event.guild),
                'member': event.member === null ? null : ScriptObjects.member(event.member, true),
                'user': ScriptObjects.user(event.message.author, true),
                'message': ScriptObjects.message(event.message, true),
                'channel': ScriptObjects.messageChannel(event.channel, true)
        ]
        final var future = EVALUATION_EXECUTOR.submit(() -> {
            try {
                ScriptRunner.run(context, finalScript);
            } catch (Exception exception) {
                event.getMessage().reply("There was an exception evaluating script: "
                        + exception.getLocalizedMessage()).setAllowedMentions(ScriptObjects.ALLOWED_MENTIONS)
                        .setActionRow(DismissListener.createDismissButton(event.getAuthor())).queue();
            }
        });
        Constants.EXECUTOR.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                event.getMessage().reply("Evaluation was timed out!")
                        .setActionRow(DismissListener.createDismissButton(event.getAuthor())).queue();
            }
        }, 4, TimeUnit.SECONDS);
    }

    static Map<String, ScriptArgument> createInteractionContext(final InteractionHook hook) {
        final inter = hook.interaction
        return [
                'guild' : ScriptObjects.guild(inter.guild),
                'channel': ScriptObjects.messageChannel(inter.messageChannel, true),
                'member': inter.member === null ? null : ScriptObjects.member(inter.member, true),
                'user'  : ScriptObjects.user(inter.user, true),
                'hook'  : ScriptObjects.hook(hook)
        ]
    }
}
