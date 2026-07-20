package test;

import configs.BinOpAgent;
import configs.Graph;
import configs.Node;
import graph.Agent;
import graph.Message;
import graph.TopicManagerSingleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GraphTest {

    public static void main(String[] args) {
        testNodeAccessorsAndDefensiveCopies();
        testRepeatedCycleDetection();
        testAcyclicGraphDetection();
        testGraphFromTopicsNamesEdgesAndNoDuplicates();
        testBinOpAgentNormalCalculation();
        testBinOpAgentRejectsNaN();
        testBinOpAgentReusesLatestValues();
        testBinOpAgentResetToZero();
        testBinOpAgentCloseUnregisters();

        System.out.println("GraphTest passed");
    }

    private static void testNodeAccessorsAndDefensiveCopies() {
        Node a = new Node("A:first");
        Node b = new Node("T:second");

        a.setName("A:renamed");
        assertEquals("A:renamed", a.getName(), "setName/getName should update the name");

        a.addEdge(b);
        assertEquals(1, a.getEdges().size(), "addEdge should add one edge");

        List<Node> copy = a.getEdges();
        copy.clear();
        assertEquals(1, a.getEdges().size(), "getEdges should not expose the internal edge list");

        Node c = new Node("T:third");
        List<Node> edges = new ArrayList<>();
        edges.add(c);
        a.setEdges(edges);
        edges.clear();
        assertEquals(1, a.getEdges().size(), "setEdges should copy the supplied list");
        assertTrue(a.getEdges().contains(c), "setEdges should preserve supplied nodes");

        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        a.setMessage(message);
        message[0] = 'j';
        assertEquals("hello", new String(a.getMessage(), StandardCharsets.UTF_8), "setMessage should copy bytes");

        byte[] readBack = a.getMessage();
        readBack[1] = 'a';
        assertEquals("hello", new String(a.getMessage(), StandardCharsets.UTF_8), "getMessage should return a copy");

        a.setMessage(null);
        assertTrue(a.getMessage() == null, "setMessage(null) should clear the message");
    }

    private static void testRepeatedCycleDetection() {
        Node a = new Node("A:a");
        Node b = new Node("T:b");
        Node c = new Node("A:c");

        a.addEdge(b);
        b.addEdge(c);
        c.addEdge(a);

        assertTrue(a.hasCycles(), "cycle should be detected on first call");
        assertTrue(a.hasCycles(), "cycle should be detected on repeated direct call");
        assertTrue(b.hasCycles(), "cycle should be detected from another node in the cycle");
    }

    private static void testAcyclicGraphDetection() {
        Node a = new Node("T:a");
        Node b = new Node("A:b");
        Node c = new Node("T:c");

        a.addEdge(b);
        b.addEdge(c);

        assertFalse(a.hasCycles(), "acyclic graph should not report cycles");
        assertFalse(a.hasCycles(), "acyclic graph should stay acyclic on repeated call");
    }

    private static void testGraphFromTopicsNamesEdgesAndNoDuplicates() {
        TopicManagerSingleton.get().clear();
        BinOpAgent add = new BinOpAgent("add", "left", "right", "sum", Double::sum);

        Graph graph = new Graph();
        graph.createFromTopics();

        assertEquals(4, graph.size(), "graph should contain one agent node and three topic nodes");
        assertTrue(hasNode(graph, "T:left"), "graph should contain T:left");
        assertTrue(hasNode(graph, "T:right"), "graph should contain T:right");
        assertTrue(hasNode(graph, "T:sum"), "graph should contain T:sum");
        assertTrue(hasNode(graph, "A:add"), "graph should contain A:add");

        assertTrue(hasEdge(graph, "T:left", "A:add"), "input topic should point to subscriber agent");
        assertTrue(hasEdge(graph, "T:right", "A:add"), "second input topic should point to subscriber agent");
        assertTrue(hasEdge(graph, "A:add", "T:sum"), "publisher agent should point to output topic");
        assertFalse(graph.hasCycles(), "simple bin-op graph should be acyclic");

        graph.createFromTopics();
        assertEquals(4, graph.size(), "recreating from topics should not duplicate nodes");

        add.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testBinOpAgentNormalCalculation() {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("out");
        BinOpAgent add = new BinOpAgent("add", "x", "y", "out", Double::sum);

        TopicManagerSingleton.get().getTopic("x").publish(new Message(2.0));
        assertEquals(0, capture.values.size(), "agent should wait until both inputs are known");
        TopicManagerSingleton.get().getTopic("y").publish(new Message(3.0));

        assertDoubles(Arrays.asList(5.0), capture.values, "2 + 3 should publish 5");

        add.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testBinOpAgentRejectsNaN() {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("out");
        BinOpAgent add = new BinOpAgent("add", "x", "y", "out", Double::sum);

        TopicManagerSingleton.get().getTopic("x").publish(new Message("bad-number"));
        TopicManagerSingleton.get().getTopic("y").publish(new Message(3.0));
        assertEquals(0, capture.values.size(), "NaN input should not publish");

        TopicManagerSingleton.get().getTopic("x").publish(new Message(2.0));
        assertDoubles(Arrays.asList(5.0), capture.values, "valid replacement input should publish with latest y");

        add.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testBinOpAgentReusesLatestValues() {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("out");
        BinOpAgent add = new BinOpAgent("add", "x", "y", "out", Double::sum);

        TopicManagerSingleton.get().getTopic("x").publish(new Message(2.0));
        TopicManagerSingleton.get().getTopic("y").publish(new Message(3.0));
        TopicManagerSingleton.get().getTopic("x").publish(new Message(4.0));

        assertDoubles(Arrays.asList(5.0, 7.0), capture.values, "latest y value should be reused after x changes");

        add.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testBinOpAgentResetToZero() {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("out");
        BinOpAgent add = new BinOpAgent("add", "x", "y", "out", Double::sum);

        add.reset();
        TopicManagerSingleton.get().getTopic("x").publish(new Message(9.0));

        assertDoubles(Arrays.asList(9.0), capture.values, "after reset, y should be the stored zero value");

        add.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testBinOpAgentCloseUnregisters() {
        TopicManagerSingleton.get().clear();
        BinOpAgent add = new BinOpAgent("add", "x", "y", "out", Double::sum);

        assertTrue(TopicManagerSingleton.get().getTopic("x").getSubscribers().contains(add), "x should contain agent subscriber");
        assertTrue(TopicManagerSingleton.get().getTopic("y").getSubscribers().contains(add), "y should contain agent subscriber");
        assertTrue(TopicManagerSingleton.get().getTopic("out").getPublishers().contains(add), "out should contain agent publisher");

        add.close();

        assertFalse(TopicManagerSingleton.get().getTopic("x").getSubscribers().contains(add), "x should remove agent subscriber on close");
        assertFalse(TopicManagerSingleton.get().getTopic("y").getSubscribers().contains(add), "y should remove agent subscriber on close");
        assertFalse(TopicManagerSingleton.get().getTopic("out").getPublishers().contains(add), "out should remove agent publisher on close");

        TopicManagerSingleton.get().clear();
    }

    private static CaptureAgent subscribeCapture(String topic) {
        CaptureAgent capture = new CaptureAgent("capture-" + topic);
        TopicManagerSingleton.get().getTopic(topic).subscribe(capture);
        return capture;
    }

    private static boolean hasNode(Graph graph, String name) {
        return findNode(graph, name) != null;
    }

    private static boolean hasEdge(Graph graph, String from, String to) {
        Node fromNode = findNode(graph, from);
        Node toNode = findNode(graph, to);
        return fromNode != null && toNode != null && fromNode.getEdges().contains(toNode);
    }

    private static Node findNode(Graph graph, String name) {
        for (Node node : graph) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    private static void assertDoubles(List<Double> expected, List<Double> actual, String message) {
        assertEquals(expected.size(), actual.size(), message + " size");
        for (int i = 0; i < expected.size(); i++) {
            if (Math.abs(expected.get(i) - actual.get(i)) > 0.000001) {
                throw new AssertionError(message + " expected " + expected + " but got " + actual);
            }
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

    private static class CaptureAgent implements Agent {
        private final String name;
        private final List<Double> values = new ArrayList<>();

        private CaptureAgent(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void reset() {
            values.clear();
        }

        @Override
        public void callback(String topic, Message msg) {
            values.add(msg.asDouble);
        }

        @Override
        public void close() {
            values.clear();
        }
    }
}