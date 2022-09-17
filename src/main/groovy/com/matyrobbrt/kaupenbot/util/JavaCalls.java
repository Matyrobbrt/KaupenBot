package com.matyrobbrt.kaupenbot.util;

import com.matyrobbrt.jdahelper.components.context.ButtonInteractionContext;
import com.matyrobbrt.jdahelper.pagination.PaginatorImpl;
import com.matyrobbrt.kaupenbot.extensions.Extensions;
import groovy.lang.Closure;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class JavaCalls {
    public static int div(int i1, int i2) {
        return i1 / i2;
    }
    public static int mod(int i1, int i2) {
        return i1 % i2;
    }

    public static Consumer<ButtonInteractionContext> deferringHandler(PaginatedSlashCommand command) {
        return ctx -> {
            ctx.getEvent().deferEdit().queue();
            ((PaginatorImpl) command.paginator).onButtonInteraction(ctx);
        };
    }

    @Nullable
    public static Long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Consumer<SlashCommandInteractionEvent> slashCallback(final List<Predicate<SlashCommandInteractionEvent>> predicates, final Closure<?> closure) {
        return event -> {
            for (final var pred : predicates)
                if (!pred.test(event)) return;
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(event);
            closure.call(event);
        };
    }

    public static Predicate<SlashCommandInteractionEvent> slashPredicate(final Predicate<SlashCommandInteractionEvent> predicate, @Nullable final String message) {
        if (message == null) return predicate;
        return event -> {
            if (!predicate.test(event)) {
                Extensions.replyProhibited(event, message).queue();
                return false;
            }
            return true;
        };
    }
}
