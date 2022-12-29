package com.matyrobbrt.kaupenbot.common.util;

import com.matyrobbrt.jdahelper.components.context.ButtonInteractionContext;
import com.matyrobbrt.jdahelper.pagination.PaginatorImpl;
import com.matyrobbrt.kaupenbot.common.command.PaginatedCommandBuilder;
import com.matyrobbrt.kaupenbot.common.command.PaginatedSlashCommand;
import com.matyrobbrt.kaupenbot.extensions.Extensions;
import groovy.lang.Closure;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
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
    public static Consumer<ButtonInteractionContext> deferringHandler(PaginatedCommandBuilder command) {
        return ctx -> {
            ctx.getEvent().deferEdit().queue();
            ((PaginatorImpl) command.getPaginator()).onButtonInteraction(ctx);
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

    public static <T> List<? extends LayoutComponent> makeButtonsFrom(Collection<T> objects, Function<? super T, Button> buttonFunction) {
        final List<List<T>> splitInLists = new ArrayList<>();
        splitInLists.add(new ArrayList<>());
        for (final T obj : objects) {
            List<T> current = splitInLists.get(splitInLists.size() - 1);
            if (current.size() >= Component.Type.BUTTON.getMaxPerRow()) {
                current = new ArrayList<>();
                splitInLists.add(current);
            }
            current.add(obj);
        }
        return splitInLists.stream().map(it -> ActionRow.of(it.stream().map(buttonFunction).toList())).toList();
    }
}
