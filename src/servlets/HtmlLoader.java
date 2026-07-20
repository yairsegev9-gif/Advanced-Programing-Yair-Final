package servlets;

import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


public class HtmlLoader implements Servlet {
    private final Path baseFolder;

    public HtmlLoader(String folder) {
        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException("folder cannot be blank");
        }
        this.baseFolder = Path.of(folder).normalize();
    }

    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        String uri = ri.getUri();
        int query = uri.indexOf('?');
        if (query >= 0) {
            uri = uri.substring(0, query);
        }
        String fileName = uri.startsWith("/app/") ? uri.substring("/app/".length()) : uri;
        if (fileName.isBlank() || fileName.endsWith("/")) {
            fileName = "index.html";
        }
        Path target = baseFolder.resolve(fileName).normalize();
        if (!target.startsWith(baseFolder) || !Files.exists(target) || Files.isDirectory(target)) {
            byte[] bytes = "<html><body>File not found</body></html>"
                    .getBytes(StandardCharsets.UTF_8);

            toClient.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: " + bytes.length + "\r\n" +
                            "Connection: close\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8));

            toClient.write(bytes);
            return;
        }
        byte[] bytes = Files.readAllBytes(target);
        toClient.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        toClient.write(bytes);
    }

    @Override
    public void close() {
    }
}