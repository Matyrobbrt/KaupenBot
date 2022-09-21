package com.matyrobbrt.kaupenbot.quote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.matyrobbrt.kaupenbot.KaupenBot
import com.matyrobbrt.kaupenbot.common.util.JavaCalls
import com.matyrobbrt.kaupenbot.util.gson.InstantTypeAdapter
import groovy.transform.CompileStatic
import groovy.transform.DefaultsMode
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.utils.TimeFormat

import javax.annotation.Nullable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@CompileStatic
final class Quotes {
    private static final Path QUOTE_STORAGE = Path.of('quotes.json')

    /**
     * The Gson instance used for serialization and deserialization.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant, new InstantTypeAdapter())
            .create()

    /**
     * A map of guild IDs to a list of Quote instances held by this container.
     * Quote metadata ID should sync with the index in this list.
     * This greatly simplifies access operations.
     */
    private static Map<Long, List<Quote>> quotes = null

    static final Quote NULL = new Quote(null, null,  null, null)

    /**
     * The message used for when quotes are null, or do not exist.
     */
    static final String QUOTE_NOT_PRESENT = "⚠ Quote does not exist."

    @Nullable
    static Quote getQuote(final long guildId, final int id) {
        return getQuotesForGuild(guildId).get(id)
    }

    static void loadQuotes() {
        if (quotes != null) {
            return
        }

        final var path = QUOTE_STORAGE
        if (!Files.exists(path)) {
            quotes = Collections.synchronizedMap(new HashMap<>())
            return
        }
        try {
            try (final reader = path.newReader()) {
                quotes = GSON.fromJson(reader, new TypeToken<Map<Long, List<Quote>>>() {}.getType())
            }
        } catch (final IOException exception) {
            KaupenBot.log.error('Failed to read quote file: ', exception)
            quotes = Collections.synchronizedMap(new HashMap<>())
        }
    }

    /**
     * Write quotes to disk.
     * <p>
     * Has minimal error handling.
     */
    private static void syncQuotes() {
        if (quotes == null) {
            loadQuotes()
        }
        try (final var writer = QUOTE_STORAGE.newWriter(false)) {
            GSON.toJson(quotes, writer)
        } catch (Exception exception) {
            KaupenBot.log.error('Failed to write quote file: ', exception)
        }
    }

    static void addQuote(final long guildId, final Quote quote, final int id = quote.id) {
        getQuotesForGuild(guildId).add(id, quote)
        syncQuotes()
    }

    static void removeQuote(final long guildId, final int id) {
        final var quotes = getQuotesForGuild(guildId)
        quotes.set(id, NULL)

        // Count the number of nulls at the end of the list
        int index = 0
        int nullElements = 0
        while (index < quotes.size()) {
            if (quotes.get(index) != NULL) {
                nullElements = 0
                index++
                continue
            }

            nullElements++
            index++
        }

        // Remove extras if necessary
        if (nullElements > 0)
            quotes.subList(quotes.size() - nullElements, quotes.size()).clear()

        // Write to disk
        syncQuotes()
    }

    static int getQuoteSlot(final long guildId) {
        return getQuotesForGuild(guildId).size()
    }

    static List<Quote> getQuotesForGuild(final long guildId) {
        if (quotes == null) {
            loadQuotes()
        }
        return quotes.computeIfAbsent(guildId, k -> new ArrayList<>())
    }
}

@CompileStatic
@TupleConstructor(excludes = 'id', defaultsMode = DefaultsMode.OFF)
@EqualsAndHashCode
final class Quote {
    final String quote
    final String quotee
    final String author
    final Instant timestamp

    int id

    String resolve(JDA jda) {
        final quotee = resolveQuotee(jda)
        final author = resolveAuthor(jda)
        return """
📑 #${id + 1}:
> $quote
- $quotee
✍🏼 $author
📅 ${TimeFormat.DATE_TIME_SHORT.format(timestamp)}
"""
    }

    String resolveQuotee(JDA jda) {
        JavaCalls.parseLong(this.quotee)?.with {
            jda.retrieveUserById(it).useCache(true)
                    .submit().thenApply(u -> u.asTag)
                    .exceptionally(i -> null)
                    .get()
        } ?: this.quotee
    }
    String resolveAuthor(JDA jda) {
        JavaCalls.parseLong(this.author)?.with {
            jda.retrieveUserById(it).useCache(true)
                    .submit().thenApply(u -> u.asTag)
                    .exceptionally(i -> null)
                    .get()
        } ?: this.author
    }
}
