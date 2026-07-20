package views;

import configs.Graph;
import configs.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class HtmlGraphWriter {
    public static List<String> getGraphHTML(Graph graph) {
        List<String> lines = new ArrayList<>();
        lines.add("<!DOCTYPE html>");
        lines.add("<html><head><meta charset=\"UTF-8\"><title>Computational Graph</title>");
        lines.add("<style>body{font-family:Arial,sans-serif;margin:0;padding:16px;background:#f7f9fb;color:#1f2933}svg{width:100%;min-height:520px;border:1px solid #d7dee8;background:white}.topic{fill:#e8f4ff;stroke:#276fbf}.agent{fill:#fff3d6;stroke:#b7791f}.edge{stroke:#4a5568;stroke-width:2;marker-end:url(#arrow)}text{font-size:13px;text-anchor:middle;dominant-baseline:middle}</style>");
        lines.add("</head><body><h2>Computational Graph</h2>");
        if (graph == null || graph.isEmpty()) {
            lines.add("<p>No graph loaded.</p></body></html>");
            return lines;
        }
        Map<Node, int[]> positions = new HashMap<>();
        int index = 0;
        for (Node node : graph) {
            int x = 120 + (index % 4) * 190;
            int y = 90 + (index / 4) * 130;
            positions.put(node, new int[]{x, y});
            index++;
        }
        lines.add("<svg viewBox=\"0 0 900 650\" role=\"img\" aria-label=\"Computational graph\">");
        lines.add("<defs><marker id=\"arrow\" markerWidth=\"10\" markerHeight=\"10\" refX=\"9\" refY=\"3\" orient=\"auto\"><path d=\"M0,0 L0,6 L9,3 z\" fill=\"#4a5568\"/></marker></defs>");
        for (Node from : graph) {
            int[] p1 = positions.get(from);
            for (Node to : from.getEdges()) {
                int[] p2 = positions.get(to);
                if (p1 != null && p2 != null) {
                    double dx = p2[0] - p1[0];
                    double dy = p2[1] - p1[1];
                    double length = Math.sqrt(dx * dx + dy * dy);

                    if (length > 0) {
                        double targetRadius = to.getName().startsWith("T") ? 58 : 42;

                        double x2 = p2[0] - (dx / length) * targetRadius;
                        double y2 = p2[1] - (dy / length) * targetRadius;

                        lines.add(
                                "<line class=\"edge\" x1=\"" + p1[0] +
                                        "\" y1=\"" + p1[1] +
                                        "\" x2=\"" + x2 +
                                        "\" y2=\"" + y2 +
                                        "\"/>"
                        );
                    }
                }
            }
        }
        for (Node node : graph) {
            int[] p = positions.get(node);
            String name = escape(node.getName());
            if (node.getName().startsWith("T")) {
                lines.add("<rect class=\"topic\" x=\"" + (p[0] - 55) + "\" y=\"" + (p[1] - 24) + "\" width=\"110\" height=\"48\" rx=\"4\"/>");
            } else {
                lines.add("<circle class=\"agent\" cx=\"" + p[0] + "\" cy=\"" + p[1] + "\" r=\"38\"/>");
            }
            lines.add("<text x=\"" + p[0] + "\" y=\"" + p[1] + "\">" + name + "</text>");
        }
        lines.add("</svg></body></html>");
        return lines;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}