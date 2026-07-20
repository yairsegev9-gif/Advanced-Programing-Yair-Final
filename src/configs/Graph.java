package configs;

import graph.Agent;
import graph.Topic;
import graph.TopicManagerSingleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Directed computational graph built from the current TopicManager state.
 * Topic nodes are named {@code T:<topic>} and agent nodes are named
 * {@code A:<agent>}.
 */
public class Graph extends ArrayList<Node> {

    public void createFromTopics() {
        clear();

        Map<String, Node> nodesByName = new HashMap<>();

        for (Topic topic : TopicManagerSingleton.get().getTopics()) {
            Node topicNode = nodesByName.computeIfAbsent(
                    "T:" + topic.name,
                    Node::new
            );

            for (Agent publisher : topic.getPublishers()) {
                Node publisherNode = nodesByName.computeIfAbsent(
                        "A:" + publisher.getName(),
                        Node::new
                );

                publisherNode.addEdge(topicNode);
            }

            for (Agent subscriber : topic.getSubscribers()) {
                Node subscriberNode = nodesByName.computeIfAbsent(
                        "A:" + subscriber.getName(),
                        Node::new
                );

                topicNode.addEdge(subscriberNode);
            }
        }

        addAll(nodesByName.values());
    }

    public boolean hasCycles() {
        resetAllStates();

        for (Node node : this) {
            if (node.hasCycles()) {
                resetAllStates();
                return true;
            }
        }

        resetAllStates();
        return false;
    }

    private void resetAllStates() {
        for (Node node : this) {
            node.resetState();
        }
    }
}