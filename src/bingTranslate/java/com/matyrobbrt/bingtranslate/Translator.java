package com.matyrobbrt.bingtranslate;

import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.MultipartBodyPublisher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttp;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Translator {
    public static final String API_ROOT = "https://%sbing.com";
    public static final String TRANSLATE_WEBSITE = API_ROOT + "/translator";
    public static final String TRANSLATE_API = API_ROOT + "/ttranslatev3";
    public static final int MAX_TEXT_LENGTH = 1000;

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.5195.127 Safari/537.36";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Language.class, new Language.TypeAdapter()).create();

    private static GlobalConfig globalConfig;

    public static TranslationResult.WithResponse translate(String text, @Nullable Language from, Language to) throws IOException, InterruptedException {
        if (globalConfig == null || globalConfig.isExpired()) {
            globalConfig = GlobalConfig.fetch();
        }

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(makeRequestURL(globalConfig)))
                .POST(formUrlencoded(makeRequestBody(globalConfig, text, from, to)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("user-agent", USER_AGENT)
                .header("referer", replaceSubdomain(TRANSLATE_WEBSITE, globalConfig.subdomain))
                .header("cookie", globalConfig.cookie)
                .build();

        final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 205) { // We have expired credentials, try again with a new config
            globalConfig = GlobalConfig.fetch();
            return translate(text, from, to);
        }

        if (response.statusCode() == 401) {
            return new TranslationResult.WithResponse(null, TranslationResult.Response.EXCEEDED_MAX_TRANSLATION_AMOUNT);
        }

        final JsonElement body = GSON.fromJson(response.body(), JsonElement.class);
        if (body.isJsonObject() && body.getAsJsonObject().has("ShowCaptcha")) {
            return new TranslationResult.WithResponse(null, TranslationResult.Response.REQUIRES_CAPTCHA);
        }

        return new TranslationResult.WithResponse(GSON.fromJson(body.getAsJsonArray().get(0), TranslationResult.class), TranslationResult.Response.OK);
    }

    public static String replaceSubdomain(String url, String subdomain) {
        return url.formatted(subdomain.isEmpty() ? "" : subdomain + ".");
    }

    public static String makeRequestURL(GlobalConfig config) {
        return replaceSubdomain(TRANSLATE_API, config.subdomain)
                + "?isVertical=" + (config.isVertical ? 1 : 0)
                + (config.IG.isBlank() ? "" : "&IG=" + config.IG)
                + (config.IID.isBlank() ? "" : "&IID=" + config.IID + "." + config.cnt.getAndIncrement());
    }

    public static Map<String, Object> makeRequestBody(GlobalConfig config, String text, @Nullable Language fromLang, Language toLang) {
        return makeRequestBody(config, text, fromLang == null ? "auto-detect" : fromLang.getCode(), toLang.getCode());
    }

    public static Map<String, Object> makeRequestBody(GlobalConfig config, String text, String fromLang, @Nullable String toLang) {
        final Map<String, Object> params = new HashMap<>();
        params.put("fromLang", fromLang);
        params.put("text", text);
        params.put("token", config.token);
        params.put("key", config.generatedAt);
        if (toLang != null) {
            params.put("to", toLang);
        }
        return params;
    }

    public static HttpRequest.BodyPublisher formUrlencoded(Map<String, Object> map) {
        return HttpRequest.BodyPublishers.ofString(map.entrySet()
                .stream()
                .map(entry -> String.join("=",
                        URLEncoder.encode(entry.getKey(), UTF_8),
                        URLEncoder.encode(entry.getValue().toString(), UTF_8))
                ).collect(Collectors.joining("&")));
    }

    public record GlobalConfig(String subdomain, String cookie, String IG, String IID, long generatedAt, String token, long expiryInterval, Instant expiry, boolean isVertical, AtomicInteger cnt) {

        public static GlobalConfig fetch() throws IOException, InterruptedException {
            final HttpRequest request = HttpRequest.newBuilder()
                    .header("user-agent", USER_AGENT)
                    .GET()
                    .uri(URI.create(replaceSubdomain(TRANSLATE_WEBSITE, ""))).build();
            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            final String redirect = response.uri().toString();
            final String subdomain = match(redirect, "https?:\\/\\/(\\w+)\\.bing\\.com", 1);
            final String bodyStr = response.body();

            final String cookie = response.headers().allValues("set-cookie").stream().map(it -> it.split(";")[0]).collect(Collectors.joining("; "));

            final String IG = findBetweenQuotes(bodyStr, "IG:");
            final String IID = findBetweenQuotes(bodyStr, "data-iid=");

            final JsonArray body = GSON.fromJson(match(bodyStr, "params_RichTranslateHelper\\s?=\\s?([^\\]]+\\])", 1), JsonArray.class);
            final long generatedAt = body.get(0).getAsLong();
            final long expiryInterval = body.get(2).getAsLong();
            return new GlobalConfig(subdomain, cookie, IG, IID, generatedAt, body.get(1).getAsString(), expiryInterval, Instant.ofEpochMilli(generatedAt).plusMillis(expiryInterval), body.get(3).getAsBoolean(), new AtomicInteger());
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }

    private static String findBetweenQuotes(String str, String startText) {
        final int idx = str.indexOf(startText + "\"") + startText.length() + 1;
        return str.substring(idx, str.indexOf("\"", idx));
    }

    private static String match(String str, String regex, int index) {
        final Matcher matcher = Pattern.compile(regex).matcher(str);
        matcher.find();
        return matcher.group(index);
    }
}
