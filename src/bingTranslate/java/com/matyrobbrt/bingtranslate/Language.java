package com.matyrobbrt.bingtranslate;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Language {
    AFRIKAANS("af", "Afrikaans"),
    ALBANIAN("sq", "Albanian"),
    AMHARIC("am", "Amharic"),
    ARABIC("ar", "Arabic"),
    ARMENIAN("hy", "Armenian"),
    ASSAMESE("as", "Assamese"),
    AZERBAIJANI("az", "Azerbaijani"),
    BANGLA("bn", "Bangla"),
    BASHKIR("ba", "Bashkir"),
    BASQUE("eu", "Basque"),
    BOSNIAN("bs", "Bosnian"),
    BULGARIAN("bg", "Bulgarian"),
    CANTONESE_TRADITIONAL("yue", "Cantonese (Traditional)"),
    CATALAN("ca", "Catalan"),
    CHINESE_LITERARY("lzh", "Chinese (Literary)"),
    CHINESE_SIMPLIFIED("zh-Hans", "Chinese Simplified"),
    CHINESE_TRADITIONAL("zh-Hant", "Chinese Traditional", countryCodeToEmoji("CN")),
    CROATIAN("hr", "Croatian"),
    CZECH("cs", "Czech"),
    DANISH("da", "Danish"),
    DARI("prs", "Dari"),
    DIVEHI("dv", "Divehi"),
    DUTCH("nl", "Dutch"),
    ENGLISH("en", "English", countryCodeToEmoji("US")),
    ESTONIAN("et", "Estonian"),
    FAROESE("fo", "Faroese"),
    FIJIAN("fj", "Fijian"),
    FILIPINO("fil", "Filipino"),
    FINNISH("fi", "Finnish"),
    FRENCH("fr", "French"),
    FRENCH_CANADA("fr-CA", "French (Canada)"),
    GALICIAN("gl", "Galician"),
    GEORGIAN("ka", "Georgian"),
    GERMAN("de", "German"),
    GREEK("el", "Greek", countryCodeToEmoji("GR")),
    GUJARATI("gu", "Gujarati"),
    HAITIAN_CREOLE("ht", "Haitian Creole"),
    HEBREW("he", "Hebrew"),
    HINDI("hi", "Hindi", countryCodeToEmoji("IN")),
    HMONG_DAW("mww", "Hmong Daw"),
    HUNGARIAN("hu", "Hungarian"),
    ICELANDIC("is", "Icelandic"),
    INDONESIAN("id", "Indonesian"),
    INUINNAQTUN("ikt", "Inuinnaqtun"),
    INUKTITUT("iu", "Inuktitut"),
    INUKTITUT_LATIN("iu-Latn", "Inuktitut (Latin)"),
    IRISH("ga", "Irish"),
    ITALIAN("it", "Italian"),
    JAPANESE("ja", "Japanese", countryCodeToEmoji("JP")),
    KANNADA("kn", "Kannada"),
    KAZAKH("kk", "Kazakh"),
    KHMER("km", "Khmer"),
    KLINGON_LATIN("tlh-Latn", "Klingon (Latin)"),
    KOREAN("ko", "Korean"),
    KURDISH_CENTRAL("ku", "Kurdish (Central)"),
    KURDISH_NORTHERN("kmr", "Kurdish (Northern)"),
    KYRGYZ("ky", "Kyrgyz"),
    LAO("lo", "Lao"),
    LATVIAN("lv", "Latvian"),
    LITHUANIAN("lt", "Lithuanian"),
    MACEDONIAN("mk", "Macedonian"),
    MALAGASY("mg", "Malagasy"),
    MALAY("ms", "Malay"),
    MALAYALAM("ml", "Malayalam"),
    MALTESE("mt", "Maltese"),
    MARATHI("mr", "Marathi"),
    MONGOLIAN_CYRILLIC("mn-Cyrl", "Mongolian (Cyrillic)"),
    MONGOLIAN_TRADITIONAL("mn-Mong", "Mongolian (Traditional)"),
    MYANMAR_BURMESE("my", "Myanmar (Burmese)"),
    MAORI("mi", "Maori"),
    NEPALI("ne", "Nepali"),
    NORWEGIAN("nb", "Norwegian"),
    ODIA("or", "Odia"),
    PASHTO("ps", "Pashto"),
    PERSIAN("fa", "Persian"),
    POLISH("pl", "Polish"),
    PORTUGUESE_BRAZIL("pt", "Portuguese (Brazil)"),
    PORTUGUESE_PORTUGAL("pt-PT", "Portuguese (Portugal)"),
    PUNJABI("pa", "Punjabi"),
    QUERETARO_OTOMI("otq", "Queretaro Otomi"),
    ROMANIAN("ro", "Romanian"),
    RUSSIAN("ru", "Russian"),
    SAMOAN("sm", "Samoan"),
    SERBIAN_CYRILLIC("sr-Cyrl", "Serbian (Cyrillic)"),
    SERBIAN_LATIN("sr-Latn", "Serbian (Latin)"),
    SLOVAK("sk", "Slovak"),
    SLOVENIAN("sl", "Slovenian"),
    SOMALI("so", "Somali"),
    SPANISH("es", "Spanish"),
    SWAHILI("sw", "Swahili"),
    SWEDISH("sv", "Swedish"),
    TAHITIAN("ty", "Tahitian"),
    TAMIL("ta", "Tamil"),
    TATAR("tt", "Tatar"),
    TELUGU("te", "Telugu"),
    THAI("th", "Thai"),
    TIBETAN("bo", "Tibetan"),
    TIGRINYA("ti", "Tigrinya"),
    TONGAN("to", "Tongan"),
    TURKISH("tr", "Turkish"),
    TURKMEN("tk", "Turkmen"),
    UKRAINIAN("uk", "Ukrainian", countryCodeToEmoji("UA")),
    UPPER_SORBIAN("hsb", "Upper Sorbian"),
    URDU("ur", "Urdu"),
    UYGHUR("ug", "Uyghur"),
    UZBEK_LATIN("uz", "Uzbek (Latin)"),
    VIETNAMESE("vi", "Vietnamese"),
    WELSH("cy", "Welsh"),
    YUCATEC_MAYA("yua", "Yucatec Maya"),
    ZULU("zu", "Zulu");

    public static final Map<String, Language> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(Language::getCode, Function.identity()));

    public static Language byCode(String code) {
        return BY_CODE.get(code);
    }

    private final String code, name;
    private final String flag;

    Language(String code, String name, String flag) {
        this.code = code;
        this.name = name;
        this.flag = flag;
    }

    Language(String code, String name) {
        this(code, name, countryCodeToEmoji(code.toUpperCase(Locale.ROOT)));
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
    public String getFlag() {
        return flag;
    }

    public String getDisplay() {
        return flag.isBlank() ? name : (flag + " " + name);
    }

    @Nullable
    public Emoji asEmoji() {
        return flag.isBlank() ? null : Emoji.fromUnicode(flag);
    }

    @Override
    public String toString() {
        return name;
    }

    private static String countryCodeToEmoji(String code) {
        // offset between uppercase ASCII and regional indicator symbols
        int offset = 127397;

        // validate code
        if(code == null || code.length() != 2) {
            return "";
        }

        //fix for uk -> gb
        if (code.equalsIgnoreCase("uk")) {
            code = "gb";
        }

        // convert code to uppercase
        code = code.toUpperCase();

        StringBuilder emojiStr = new StringBuilder();

        //loop all characters
        for (int i = 0; i < code.length(); i++) {
            emojiStr.appendCodePoint(code.charAt(i) + offset);
        }

        // return emoji
        return emojiStr.toString();
    }

    public static final class TypeAdapter implements JsonSerializer<Language>, JsonDeserializer<Language> {

        @Override
        public Language deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Language.byCode(json.getAsString());
        }

        @Override
        public JsonElement serialize(Language src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getCode());
        }
    }

}
