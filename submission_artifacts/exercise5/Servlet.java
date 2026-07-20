package test;


import java.io.IOException;
import java.io.OutputStream;

/**
 * Handles a parsed HTTP request and writes a complete HTTP response.
 * Implementations are responsible for response status line, headers, and body.
 */
public interface Servlet {
    /**
     * Handles one request.
     *
     * @param ri parsed request information
     * @param toClient output stream connected to the client socket
     * @throws IOException if writing the response fails
     */
    void handle(RequestParser.RequestInfo ri, OutputStream toClient) throws IOException;
    void close() throws IOException;
}