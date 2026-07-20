package test;

import server.MyHTTPServer;
import server.RequestParser;
import servlets.Servlet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HTTPServerTest {

    public static void main(String[] args) throws Exception {
        testRequestParser();
        testMalformedRequest();
        testServerRoutingReplacementRemovalAndConcurrentRequests();
        testInvalidServerArguments();
        System.out.println("HTTPServerTest passed");
    }

    private static void testRequestParser() throws Exception {
        String request = "POST /api/resource/item?id=123&name=test+case HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "hello";
        RequestParser.RequestInfo info = RequestParser.parseRequest(reader(request));
        assertEquals("POST", info.getHttpCommand(), "command should parse");
        assertEquals("/api/resource/item?id=123&name=test+case", info.getUri(), "uri should parse");
        assertEquals("api", info.getUriSegments()[0], "first segment should parse");
        assertEquals("resource", info.getUriSegments()[1], "second segment should parse");
        assertEquals("123", info.getParameters().get("id"), "query id should parse");
        assertEquals("test case", info.getParameters().get("name"), "query decoding should parse plus as space");
        assertEquals("hello", new String(info.getContent(), StandardCharsets.UTF_8), "body should parse by content length");
        assertEquals("example.com", info.getHeaders().get("host"), "headers should parse");

        RequestParser.RequestInfo empty = RequestParser.parseRequest(reader("GET / HTTP/1.1\r\n\r\n"));
        assertEquals(0, empty.getUriSegments().length, "root URI should have no segments");
        assertEquals(0, empty.getContent().length, "empty body should parse");
    }

    private static void testMalformedRequest() {
        expectFailure(() -> RequestParser.parseRequest(reader("BADLINE\r\n\r\n")), "malformed request should fail");
        expectFailure(() -> RequestParser.parseRequest(reader("POST / HTTP/1.1\r\nContent-Length: nope\r\n\r\n")), "bad content length should fail");
    }

    private static void testServerRoutingReplacementRemovalAndConcurrentRequests() throws Exception {
        int port = freePort();
        MyHTTPServer server = new MyHTTPServer(port, 3);
        CountingServlet root = new CountingServlet("root");
        CountingServlet api = new CountingServlet("api");
        CountingServlet apiLong = new CountingServlet("api-long");
        CountingServlet replacement = new CountingServlet("replacement");
        server.addServlet("GET", "/", root);
        server.addServlet("GET", "/app", new CountingServlet("app"));
        server.addServlet("GET", "/api/", api);
        server.addServlet("GET", "/api/long/", apiLong);
        server.addServlet("POST", "/echo", new EchoServlet());
        server.start();
        waitForServer(port);

        assertContains(send(port, "GET /api/long/value?x=1 HTTP/1.1\r\nHost: localhost\r\n\r\n"), "api-long", "longest prefix should win");
        assertContains(send(port, "GET /app/page HTTP/1.1\r\nHost: localhost\r\n\r\n"), "app", "segment prefix should match child path");
        assertContains(send(port, "GET /application HTTP/1.1\r\nHost: localhost\r\n\r\n"), "root", "segment prefix should not match /application for /app registration");
        assertContains(send(port, "GET /api/other HTTP/1.1\r\nHost: localhost\r\n\r\n"), "api", "shorter prefix should match when longest absent");
        assertContains(send(port, "POST /echo HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3\r\n\r\nabc"), "abc", "POST servlet should receive body");

        server.addServlet("GET", "/api/", replacement);
        assertContains(send(port, "GET /api/other HTTP/1.1\r\nHost: localhost\r\n\r\n"), "replacement", "servlet should be replaceable");
        server.removeServlet("GET", "/api/");
        assertContains(send(port, "GET /api/other HTTP/1.1\r\nHost: localhost\r\n\r\n"), "root", "removal should reveal shorter prefix");

        CountDownLatch latch = new CountDownLatch(6);
        List<Thread> clients = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Thread client = new Thread(() -> {
                try {
                    send(port, "GET /api/long/concurrent HTTP/1.1\r\nHost: localhost\r\n\r\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            clients.add(client);
            client.start();
        }
        assertTrue(latch.await(3000, TimeUnit.MILLISECONDS), "concurrent clients should complete");
        for (Thread client : clients) {
            client.join(1000);
        }

        assertContains(send(port, "PATCH /api HTTP/1.1\r\nHost: localhost\r\n\r\n"), "Bad Request", "unsupported method should fail safely");
        server.close();
        server.close();
        server.join(2500);
        assertFalse(server.isAlive(), "server thread should stop after close");
        assertTrue(root.closed, "server close should close servlets");
    }

    private static void testInvalidServerArguments() {
        expectFailure(() -> new MyHTTPServer(-1, 1), "negative port should fail");
        expectFailure(() -> new MyHTTPServer(0, 0), "non-positive worker count should fail");
        MyHTTPServer server = new MyHTTPServer(0, 1);
        expectFailure(() -> server.addServlet("PUT", "/", new CountingServlet("x")), "unsupported addServlet method should fail");
        server.close();
    }

    private static BufferedReader reader(String request) {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
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

    private static void expectFailure(ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void assertContains(String actual, String expectedPart, String message) {
        if (!actual.contains(expectedPart)) {
            throw new AssertionError(message + " expected response containing " + expectedPart + " but got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class CountingServlet implements Servlet {
        private final String name;
        private volatile boolean closed;

        private CountingServlet(String name) {
            this.name = name;
        }

        @Override
        public void handle(RequestParser.RequestInfo ri, OutputStream toClient) throws IOException {
            writeResponse(toClient, name + " " + ri.getUri());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class EchoServlet implements Servlet {
        @Override
        public void handle(RequestParser.RequestInfo ri, OutputStream toClient) throws IOException {
            writeResponse(toClient, new String(ri.getContent(), StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
        }
    }

    private static void writeResponse(OutputStream out, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }
}