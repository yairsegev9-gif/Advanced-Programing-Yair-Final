package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses the subset of HTTP requests required by the project server.
 * It extracts the command, URI, URI path segments, query parameters, headers,
 * and optional request body using the Content-Length header.
 */
public class RequestParser {

    /**
     * Parses one HTTP request from a character reader.
     *
     * @param reader source containing the HTTP request
     * @return parsed request information
     * @throws IOException if the request is empty or malformed
     */
    public static RequestInfo parseRequest(BufferedReader reader) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("reader cannot be null");
        }

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            throw new IOException("empty HTTP request");
        }

        String[] requestParts = requestLine.trim().split("\\s+");
        if (requestParts.length < 2) {
            throw new IOException("malformed HTTP request line");
        }

        String command = requestParts[0].toUpperCase();
        String uri = requestParts[1];
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
            }
        }

        int contentLength = 0;
        if (headers.containsKey("content-length")) {
            try {
                contentLength = Integer.parseInt(headers.get("content-length"));
            } catch (NumberFormatException e) {
                throw new IOException("invalid Content-Length", e);
            }
            if (contentLength < 0) {
                throw new IOException("invalid Content-Length");
            }
        }

        char[] bodyChars = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = reader.read(bodyChars, read, contentLength - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        byte[] content = new String(bodyChars, 0, read).getBytes(StandardCharsets.UTF_8);

        String path = uri;
        String query = "";
        int question = uri.indexOf('?');
        if (question >= 0) {
            path = uri.substring(0, question);
            query = uri.substring(question + 1);
        }

        Map<String, String> parameters = parseQuery(query);
        String[] uriSegments = splitPath(path);
        return new RequestInfo(command, uri, uriSegments, parameters, content, headers);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> parameters = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            parameters.put(decode(key), decode(value));
        }
        return parameters;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String[] splitPath(String path) {
        String clean = path == null ? "" : path;
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/") && clean.length() > 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.isEmpty()) {
            return new String[0];
        }
        return clean.split("/");
    }

    /**
     * Immutable parsed request data passed from the HTTP server to servlets.
     */
    public static class RequestInfo {
        private final String httpCommand;
        private final String uri;
        private final String[] uriSegments;
        private final Map<String, String> parameters;
        private final byte[] content;
        private final Map<String, String> headers;

        public RequestInfo(String httpCommand, String uri, String[] uriSegments, Map<String, String> parameters, byte[] content, Map<String, String> headers) {
            this.httpCommand = httpCommand;
            this.uri = uri;
            this.uriSegments = uriSegments == null ? new String[0] : uriSegments.clone();
            this.parameters = parameters == null ? new HashMap<>() : new HashMap<>(parameters);
            this.content = content == null ? new byte[0] : content.clone();
            this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
        }

        public String getHttpCommand() {
            return httpCommand;
        }

        public String getUri() {
            return uri;
        }

        public String[] getUriSegments() {
            return uriSegments.clone();
        }

        public Map<String, String> getParameters() {
            return Collections.unmodifiableMap(parameters);
        }

        public byte[] getContent() {
            return content.clone();
        }

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }
    }
}