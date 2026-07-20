package servlets;

import configs.GenericConfig;
import configs.Graph;
import graph.TopicManagerSingleton;
import server.RequestParser.RequestInfo;
import views.HtmlGraphWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConfLoader implements Servlet {
    private GenericConfig currentConfig;
    private final Path uploadFolder;

    public ConfLoader() {
        this(Path.of("config_files"));
    }

    public ConfLoader(Path uploadFolder) {
        this.uploadFolder = uploadFolder;
    }

    @Override
    public synchronized void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        UploadedConfig uploadedConfig = parseMultipart(
                ri.getContent(),
                ri.getHeaders().get("content-type")
        );

        if (uploadedConfig == null) {
            uploadedConfig = parseTextBody(ri.getContent());
        }

        if (uploadedConfig == null || uploadedConfig.content().isBlank()) {
            TopicDisplayer.writeHtml(
                    toClient,
                    "<html><body>No configuration file supplied.</body></html>"
            );
            return;
        }

        if (currentConfig != null) {
            currentConfig.close();
        }

        TopicManagerSingleton.get().clear();

        String safeFileName = sanitizeFileName(uploadedConfig.fileName());

        Path savedFile;

        if (Files.exists(uploadFolder) && !Files.isDirectory(uploadFolder)) {
            savedFile = uploadFolder;
        } else {
            Files.createDirectories(uploadFolder);
            savedFile = uploadFolder.resolve(safeFileName);
        }

        Files.writeString(
                savedFile,
                uploadedConfig.content(),
                StandardCharsets.UTF_8
        );

        GenericConfig config = new GenericConfig();
        config.setConfFile(savedFile.toString());
        config.create();
        currentConfig = config;

        Graph graph = new Graph();
        graph.createFromTopics();

        List<String> html = HtmlGraphWriter.getGraphHTML(graph);
        TopicDisplayer.writeHtml(toClient, String.join("\n", html));
    }

    @Override
    public synchronized void close() {
        if (currentConfig != null) {
            currentConfig.close();
            currentConfig = null;
        }
    }

    private static UploadedConfig parseMultipart(
            byte[] bodyBytes,
            String contentType
    ) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }

        if (contentType == null || !contentType.contains("boundary=")) {
            return null;
        }

        String boundary = "--" + contentType.substring(
                contentType.indexOf("boundary=") + "boundary=".length()
        ).trim();

        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        int headerEnd = body.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return null;
        }

        String headers = body.substring(0, headerEnd);
        String fileName = extractFileName(headers);

        int contentStart = headerEnd + 4;
        int contentEnd = body.indexOf("\r\n" + boundary, contentStart);

        if (contentEnd < 0) {
            return null;
        }

        String content = body.substring(contentStart, contentEnd);

        return new UploadedConfig(fileName, content);
    }

    private static String extractFileName(String headers) {
        String marker = "filename=\"";
        int start = headers.indexOf(marker);

        if (start < 0) {
            return "uploaded.conf";
        }

        start += marker.length();
        int end = headers.indexOf('"', start);

        if (end < 0) {
            return "uploaded.conf";
        }

        return headers.substring(start, end);
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "uploaded.conf";
        }

        String cleaned = Path.of(fileName)
                .getFileName()
                .toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        return cleaned.isBlank() ? "uploaded.conf" : cleaned;
    }
    private static UploadedConfig parseTextBody(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }

        String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();

        if (body.startsWith("config=")) {
            String decoded = java.net.URLDecoder.decode(
                    body.substring("config=".length()),
                    StandardCharsets.UTF_8
            );

            return new UploadedConfig("uploaded.conf", decoded);
        }

        return new UploadedConfig("uploaded.conf", body);
    }
    private record UploadedConfig(String fileName, String content) {
    }
}