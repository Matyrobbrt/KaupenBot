package com.matyrobbrt.bingtranslate;

import org.jetbrains.annotations.Nullable;

public record TranslationResult(@Nullable DetectedLanguage detectedLanguage, Translation[] translations) {
    public record DetectedLanguage(Language language, double score) {}
    public record Translation(String text, Language to) {}

    public record WithResponse(@Nullable TranslationResult result, Response response) {}

    public enum Response {
        REQUIRES_CAPTCHA,
        EXCEEDED_MAX_TRANSLATION_AMOUNT,
        OK
    }
}
