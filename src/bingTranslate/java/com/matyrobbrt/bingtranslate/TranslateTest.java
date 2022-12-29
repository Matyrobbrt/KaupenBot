package com.matyrobbrt.bingtranslate;

import org.jetbrains.annotations.Nullable;

import static com.matyrobbrt.bingtranslate.Language.*;

public class TranslateTest {
    public static void main(String[] args) throws Exception {
        show("Hello!", ENGLISH, ROMANIAN);
        show("Bonjour!", null, ITALIAN);

        show("Ti odio!", ITALIAN, ENGLISH);
        final String result = show("Shut up, you idiot!", null, SPANISH);
        show(result, SPANISH, ENGLISH);
    }

    private static String show(String text, @Nullable Language from, @Nullable Language to) throws Exception {
        final TranslationResult.WithResponse response = Translator.translate(text, from, to);
        if (response.result() != null) {
            final TranslationResult result = response.result();
            System.out.printf("'%s' (%s) ----> '%s' (%s)\n", text, from == null ? (result.detectedLanguage().language() + " auto-detect") : from, result.translations()[0].text(), result.translations()[0].to());
            return result.translations()[0].text();
        } else {
            System.out.println("Translation encountered non-200 status code: " + response.response());
            return null;
        }
    }
}
