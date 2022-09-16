package com.matyrobbrt.kaupenbot.tricks

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandEvent
import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission

@CompileStatic
class AddTrickCommand extends Command {
    AddTrickCommand() {
        guildOnly = true
        name = 'addtrick'
        aliases = new String[] { 'add-trick', 'trick-add' }
        help = 'Adds a trick.'
        userPermissions = new Permission[] {
            Permission.MODERATE_MEMBERS
        }
    }
    @Override
    protected void execute(CommandEvent event) {
        final args = event.args.split('\\|')
        var script = args.drop(1).join('|')

        if (script.contains('```groovy') && script.endsWith('```')) {
            script = script.substring(script.indexOf('```groovy') + 9)
            script = script.substring(0, script.lastIndexOf('```'))
        }
        final trick = new Trick(script, args[0].split(' ').toList())

        Optional<Trick> originalTrick = Tricks.getTricks().stream()
                .filter(t -> t.getNames().stream().anyMatch(n -> trick.getNames().contains(n))).findAny();

        originalTrick.ifPresentOrElse({ old ->
            Tricks.replaceTrick(old, trick)
            event.message.reply('Updated trick!').queue()
        }, {
            Tricks.addTrick(trick)
            event.message.reply('Added trick!').queue()
        })
    }
}
