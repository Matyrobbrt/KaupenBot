package com.matyrobbrt.kaupenbot.util.util

import groovy.transform.CompileStatic
import net.dv8tion.jda.api.utils.data.DataObject

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@CompileStatic
class Gist {

    private final String description
    private final boolean isPublic
    private final Map<String, DataObject> files = new LinkedHashMap<>()

    Gist(final String description, final boolean isPublic) {
        this.description = description
        this.isPublic = isPublic
    }

    Gist(final String description, final boolean isPublic, final String filename, final String content) {
        this.description = description
        this.isPublic = isPublic
        final fileobj = DataObject.empty()
        fileobj.put('content', content)
        files.put(filename, fileobj)
    }

    Gist addFile(final String filename, final String content) {
        final fileobj = DataObject.empty()
        fileobj.put('content', content)
        files.put(filename, fileobj)
        return this
    }

    @Override
    String toString() {
        final gistobj = DataObject.empty()
        final fileobj = DataObject.empty()

        gistobj.put('public', isPublic)
        gistobj.put('description', description)

        for (final entry : files.entrySet()) {
            fileobj.put(entry.getKey(), entry.getValue())
        }

        gistobj.put('files', fileobj)
        return gistobj.toString()
    }

}

@CompileStatic
final class GistUtils {
    private static final HttpClient CLIENT = HttpClient.newHttpClient()
    static String upload(String token, final Gist gist) throws IOException {
        final target = new URL('https://api.github.com/gists')
        final request = HttpRequest.newBuilder(URI.create(target.toString()))
                .header('Content-Type', 'application/json')
                .header('Authorization', "token $token")
                .POST(HttpRequest.BodyPublishers.ofString(gist.toString()))
                .build()
        final response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        DataObject.fromJson(response.body()).getString('html_url')
    }

    static String readInputStream(InputStream s) throws IOException {
        StringBuilder content = new StringBuilder()
        try (final buffer = new BufferedReader(new InputStreamReader(s, StandardCharsets.UTF_8))) {
            String line
            while ((line = buffer.readLine()) != null) {
                content.append(line).append('\n')
            }
        }
        return content.toString()
    }
}
