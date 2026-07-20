package test;


/**
 * Minimal reusable HTTP server API used by the project controller layer.
 *
 * <p>Example:</p>
 * <pre>{@code
 * HTTPServer server = new MyHTTPServer(8080, 5);
 * server.addServlet("GET", "/app/", new HtmlLoader("html_files"));
 * server.start();
 * // later
 * server.close();
 * }</pre>
 */
public interface HTTPServer extends Runnable {
    /**
     * Registers or replaces a servlet for an HTTP command and URI prefix.
     * The implementation uses longest-prefix routing.
     *
     * @param httpCommanmd HTTP command such as GET, POST, or DELETE
     * @param uri URI prefix handled by the servlet
     * @param s servlet instance
     */
    void addServlet(String httpCommanmd, String uri, Servlet s);
    /**
     * Removes the servlet registered for the given command and URI prefix.
     *
     * @param httpCommanmd HTTP command
     * @param uri registered URI prefix
     */
    void removeServlet(String httpCommanmd, String uri);
    void start();
    void close();
}