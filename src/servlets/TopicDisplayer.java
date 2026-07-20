package servlets;

import graph.Message;
import graph.Topic;
import graph.TopicManagerSingleton;
import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;


public class TopicDisplayer implements Servlet {
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        String topicName = ri.getParameters().get("topic");
        String messageText = ri.getParameters().get("message");
        if (topicName != null && messageText != null && !topicName.isBlank()) {
            TopicManagerSingleton.get().getTopic(topicName).publish(new Message(messageText));
            waitForParallelUpdates();
        }

        StringBuilder body = new StringBuilder();
        body.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>body{font-family:Arial,sans-serif}table{border-collapse:collapse;width:100%}td,th{border:1px solid #d7dee8;padding:6px;text-align:left}</style></head><body>");
        body.append("<table><tr><th>Topic</th><th>Last Value</th></tr>");
        for (Topic topic : TopicManagerSingleton.get().getTopics()) {
            Message last = topic.getLastMessage();
            body.append("<tr><td>").append(escape(topic.name)).append("</td><td>")
                    .append(last == null ? "" : escape(last.asText)).append("</td></tr>");
        }
        body.append("</table></body></html>");
        writeHtml(toClient, body.toString());
    }

    private static void waitForParallelUpdates() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
    }

    static void writeHtml(OutputStream out, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}