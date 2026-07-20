package test;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Threaded HTTP server implementation with bounded worker concurrency.
 * The accept loop runs on this thread after {@link #start()}, while client
 * handling runs in a fixed-size worker pool. Registered servlets are stored in
 * thread-safe maps and selected by longest URI prefix.
 */
public class MyHTTPServer extends Thread implements HTTPServer {
    private final int port;
    private final ExecutorService workers;
    private final Map<String, Servlet> getServlets = new ConcurrentHashMap<>();
    private final Map<String, Servlet> postServlets = new ConcurrentHashMap<>();
    private final Map<String, Servlet> deleteServlets = new ConcurrentHashMap<>();
    private volatile boolean stopped = false;
    private ServerSocket serverSocket;

    /**
     * Creates a server instance. Call {@link #start()} to begin accepting clients
     * and {@link #close()} for shutdown.
     *
     * @param port TCP port to listen on
     * @param nThreads maximum concurrent worker threads
     */
    public MyHTTPServer(int port, int nThreads) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (nThreads <= 0) {
            throw new IllegalArgumentException("nThreads must be positive");
        }
        this.port = port;
        AtomicInteger workerNumber = new AtomicInteger(1);
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "MyHTTPServer-worker-" + workerNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        this.workers = Executors.newFixedThreadPool(nThreads, factory);
        setName("MyHTTPServer-" + port);
        setDaemon(true);
    }

    @Override
    public void addServlet(String httpCommanmd, String uri, Servlet s) {
        if (httpCommanmd == null || uri == null || s == null) {
            throw new IllegalArgumentException("command, uri, and servlet cannot be null");
        }
        servletMap(httpCommanmd).put(normalizeUri(uri), s);
    }

    @Override
    public void removeServlet(String httpCommanmd, String uri) {
        if (httpCommanmd == null || uri == null) {
            return;
        }
        Servlet removed = servletMap(httpCommanmd).remove(normalizeUri(uri));
        if (removed != null) {
            try {
                removed.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;
            ss.setSoTimeout(1000);
            while (!stopped) {
                try {
                    Socket client = ss.accept();
                    workers.submit(() -> handleClient(client));
                } catch (java.net.SocketTimeoutException ignored) {
                } catch (SocketException e) {
                    if (!stopped) {
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
        } finally {
            workers.shutdown();
        }
    }

    @Override
    public void close() {
        stopped = true;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        workers.shutdownNow();
        closeServlets();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                RequestParser.RequestInfo request = RequestParser.parseRequest(reader);
                Servlet servlet = findServlet(request.getHttpCommand(), request.getUri());
                if (servlet == null) {
                    writeTextResponse(out, 404, "Not Found", "<html><body>Not Found</body></html>");
                    return;
                }
                servlet.handle(request, out);
                out.flush();
            } catch (Exception e) {
                writeTextResponse(out, 400, "Bad Request", "<html><body>Bad Request</body></html>");
            }
        } catch (IOException ignored) {
        }
    }

    private Servlet findServlet(String command, String uri) {
        Map<String, Servlet> map = servletMap(command);
        String path = uri;
        int question = path.indexOf('?');
        if (question >= 0) {
            path = path.substring(0, question);
        }
        path = normalizeUri(path);
        String best = null;
        for (String candidate : map.keySet()) {
            if (matchesPrefix(path, candidate) && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        return best == null ? null : map.get(best);
    }

    private boolean matchesPrefix(String path, String candidate) {
        if (candidate.equals("/")) {
            return true;
        }
        if (path.equals(candidate)) {
            return true;
        }
        if (candidate.endsWith("/")) {
            return path.startsWith(candidate);
        }
        return path.startsWith(candidate + "/");
    }

    private Map<String, Servlet> servletMap(String command) {
        String normalized = command.toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "GET":
                return getServlets;
            case "POST":
                return postServlets;
            case "DELETE":
                return deleteServlets;
            default:
                throw new IllegalArgumentException("unsupported HTTP command: " + command);
        }
    }

    private static String normalizeUri(String uri) {
        String normalized = uri.isEmpty() ? "/" : uri;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private void closeServlets() {
        List<Servlet> servlets = new ArrayList<>();
        servlets.addAll(getServlets.values());
        servlets.addAll(postServlets.values());
        servlets.addAll(deleteServlets.values());
        getServlets.clear();
        postServlets.clear();
        deleteServlets.clear();
        for (Servlet servlet : servlets) {
            try {
                servlet.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void writeTextResponse(OutputStream out, int status, String statusText, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + " " + statusText + "\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }
}
