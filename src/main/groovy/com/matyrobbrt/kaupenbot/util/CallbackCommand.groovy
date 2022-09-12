package com.matyrobbrt.kaupenbot.util

import com.jagrosh.jdautilities.command.SlashCommandEvent
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

trait CallbackCommand {
    Closure cback

    void setCallback(@DelegatesTo(
            value = SlashCommandEvent,
            strategy = Closure.DELEGATE_FIRST
    ) @ClosureParams(value = SimpleType, options = 'com.jagrosh.jdautilities.command.SlashCommandEvent') Closure closure) {
        cback = closure
    }

    void execute(final SlashCommandEvent event) {
        cback.resolveStrategy = Closure.DELEGATE_FIRST
        cback.delegate = event
        cback(event)
    }
}