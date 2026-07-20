package test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A directed graph node used to visualize and validate computational graphs.
 * Nodes hold outgoing edges and optional message bytes preserved for assignment
 * compatibility.
 */
public class Node {

    private String name;
    private List<Node> edges;
    public byte[] message;

    public Node(String name) {
        setName(name);
        this.edges = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }

        this.name = name;
    }

    public List<Node> getEdges() {
        return new ArrayList<>(edges);
    }

    public void setEdges(List<Node> edges) {
        if (edges == null) {
            throw new IllegalArgumentException("edges cannot be null");
        }

        this.edges = new ArrayList<>();
        for (Node edge : edges) {
            addEdge(edge);
        }
    }

    public byte[] getMessage() {
        if (message == null) {
            return null;
        }

        return message.clone();
    }

    public void setMessage(byte[] message) {
        if (message == null) {
            this.message = null;
            return;
        }

        this.message = message.clone();
    }

    public void addEdge(Node neighbor) {
        if (neighbor == null) {
            throw new IllegalArgumentException("neighbor cannot be null");
        }

        if (!edges.contains(neighbor)) {
            edges.add(neighbor);
        }
    }

    public boolean hasCycles() {
        return hasCycles(new HashSet<>(), new HashSet<>());
    }

    private boolean hasCycles(Set<Node> visited, Set<Node> recursionStack) {
        if (recursionStack.contains(this)) {
            return true;
        }

        if (visited.contains(this)) {
            return false;
        }

        visited.add(this);
        recursionStack.add(this);

        for (Node neighbor : edges) {
            if (neighbor.hasCycles(visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(this);
        return false;
    }

    public void resetState() {
        // Kept for compatibility with earlier Graph code. Cycle detection is per-call now.
    }
}