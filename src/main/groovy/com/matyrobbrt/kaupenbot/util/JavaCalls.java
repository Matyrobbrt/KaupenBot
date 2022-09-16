package com.matyrobbrt.kaupenbot.util;

import com.matyrobbrt.jdahelper.components.context.ButtonInteractionContext;
import com.matyrobbrt.jdahelper.pagination.PaginatorImpl;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

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
}
