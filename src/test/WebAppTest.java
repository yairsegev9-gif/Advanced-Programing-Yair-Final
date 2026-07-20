package test;

import configs.Graph;
import configs.IncAgent;
import graph.Message;
import graph.TopicManagerSingleton;
import server.MyHTTPServer;
import servlets.ConfLoader;
import servlets.HtmlLoader;
import servlets.TopicDisplayer;
import views.HtmlGraphWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WebAppTest {

    public static void main(String[] args) throws Exception {
        testHtmlGraphWriter();
        testServletsDirectly();
        testEndToEndServerSmoke();
        System.out.println("WebAppTest passed");
    }

    private static void testHtmlGraphWriter() {
        TopicManagerSingleton.get().clear();
        IncAgent inc = new IncAgent(new String[]{"A"}, new String[]{"B"});
        Graph graph = new Graph();
        graph.createFromTopics();
        List<String> html = HtmlGraphWriter.getGraphHTML(graph);
        String joined = String.join("\n", html);
        assertContains(joined, "<svg", "graph HTML should contain SVG");
        assertContains(joined, "T:A", "graph HTML should contain topic node");
        assertContains(joined, "A:IncAgent", "graph HTML should contain agent node");
        inc.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testServletsDirectly() throws Exception {
        TopicManagerSingleton.get().clear();
        Path tempDir = Files.createTempDirectory("html-loader-test");
        Files.writeString(tempDir.resolve("index.html"), "<html>hello static</html>", StandardCharsets.UTF_8);
        HtmlLoader loader = new HtmlLoader(tempDir.toString());
        ByteArrayOutputStream staticOut = new ByteArrayOutputStream();
        loader.handle(new server.RequestParser.RequestInfo("GET", "/app/index.html", new String[]{"app", "index.html"}, null, null, null), staticOut);
        assertContains(staticOut.toString(StandardCharsets.UTF_8), "hello static", "HtmlLoader should load existing file");

        ByteArrayOutputStream missingOut = new ByteArrayOutputStream();
        loader.handle(new server.RequestParser.RequestInfo("GET", "/app/missing.html", new String[]{"app", "missing.html"}, null, null, null), missingOut);
        assertContains(missingOut.toString(StandardCharsets.UTF_8), "File not found", "HtmlLoader should report missing files");

        Path upload = Files.createTempFile("uploaded-config", ".conf");
        ConfLoader confLoader = new ConfLoader(upload);
        ByteArrayOutputStream graphOut = new ByteArrayOutputStream();
        String config = "configs.PlusAgent\nA,B\nC\nconfigs.IncAgent\nC\nD\n";
        confLoader.handle(new server.RequestParser.RequestInfo("POST", "/upload", new String[]{"upload"}, null, config.getBytes(StandardCharsets.UTF_8), null), graphOut);
        assertContains(graphOut.toString(StandardCharsets.UTF_8), "Computational Graph", "ConfLoader should return graph HTML");

        TopicDisplayer displayer = new TopicDisplayer();
        ByteArrayOutputStream publishA = new ByteArrayOutputStream();
        displayer.handle(requestWithParams("/publish?topic=A&message=2", "A", "2"), publishA);
        ByteArrayOutputStream publishB = new ByteArrayOutputStream();
        displayer.handle(requestWithParams("/publish?topic=B&message=3", "B", "3"), publishB);
        String table = publishB.toString(StandardCharsets.UTF_8);
        assertContains(table, "D", "TopicDisplayer should include downstream topic");
        assertContains(table, "6.0", "PlusAgent then IncAgent should produce 6");

        confLoader.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testEndToEndServerSmoke() throws Exception {
        TopicManagerSingleton.get().clear();
        int port = freePort();
        Path upload = Files.createTempFile("server-uploaded-config", ".conf");
        MyHTTPServer server = new MyHTTPServer(port, 4);
        server.addServlet("GET", "/publish", new TopicDisplayer());
        server.addServlet("POST", "/upload", new ConfLoader(upload));
        server.addServlet("GET", "/app/", new HtmlLoader("html_files"));
        server.start();
        try {
            waitForServer(port);
            assertContains(send(port, "GET /app/index.html HTTP/1.1\r\nHost: localhost\r\n\r\n"), "Advanced Programming Graph", "server should serve index.html");
            assertContains(send(port, "GET /app/nope.html HTTP/1.1\r\nHost: localhost\r\n\r\n"), "File not found", "server should report missing static file");

            String config = "configs.PlusAgent\nA,B\nC\nconfigs.IncAgent\nC\nD\n";
            String body = "config=" + URLEncoder.encode(config, StandardCharsets.UTF_8);
            String uploadResponse = send(port, "POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
            assertContains(uploadResponse, "Computational Graph", "upload should return graph HTML");

            send(port, "GET /publish?topic=A&message=4 HTTP/1.1\r\nHost: localhost\r\n\r\n");
            String values = send(port, "GET /publish?topic=B&message=5 HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertContains(values, "D", "publish response should contain output topic");
            assertContains(values, "10.0", "end-to-end graph should compute (4+5)+1");
        } finally {
            server.close();
            server.join(2500);
        }
        assertFalse(hasThreadPrefix("MyHTTPServer-worker-"), "HTTP workers should stop after smoke test");
        TopicManagerSingleton.get().clear();
    }

    private static server.RequestParser.RequestInfo requestWithParams(String uri, String topic, String message) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("topic", topic);
        params.put("message", message);
        return new server.RequestParser.RequestInfo("GET", uri, new String[]{"publish"}, params, null, null);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForServer(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(25);
            }
        }
        throw new AssertionError("server did not start on port " + port);
    }

    private static String send(int port, String request) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = socket.getInputStream().read(buffer)) >= 0) {
                response.write(buffer, 0, read);
            }
            return response.toString(StandardCharsets.UTF_8);
        }
    }

    private static boolean hasThreadPrefix(String prefix) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith(prefix) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private static void assertContains(String actual, String expectedPart, String message) {
        if (!actual.contains(expectedPart)) {
            throw new AssertionError(message + " expected response containing " + expectedPart + " but got " + actual);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}