package servlets;

import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;

public interface Servlet {

    void handle(RequestInfo ri, OutputStream toClient) throws IOException;
    void close() throws IOException;
}