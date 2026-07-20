package graph;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A named publish/subscribe channel in the computational graph.
 * Topic uses concurrent sets so subscription and publication operations can be
 * used safely by multiple agents and HTTP worker threads.
 */
public class Topic {

    public final String name;

    private final Set<Agent> subs;
    private final Set<Agent> pubs;
    private volatile Message lastMessage;

    Topic(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }

        this.name = name;
        this.subs = ConcurrentHashMap.newKeySet();
        this.pubs = ConcurrentHashMap.newKeySet();
    }

    /**
     * Registers an agent as a subscriber. Duplicate registration is ignored.
     *
     * @param agent subscriber to notify on publish
     */
    public void subscribe(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }

        subs.add(agent);
    }

    public void unsubscribe(Agent agent) {
        if (agent == null) {
            return;
        }

        subs.remove(agent);
    }

    public void addPublisher(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }

        pubs.add(agent);
    }

    public void removePublisher(Agent agent) {
        if (agent == null) {
            return;
        }

        pubs.remove(agent);
    }

    /**
     * Publishes a message to all current subscribers and remembers it as the
     * latest topic value for display purposes.
     *
     * @param msg message to deliver
     */
    public void publish(Message msg) {
        if (msg == null) {
            throw new IllegalArgumentException("message cannot be null");
        }

        lastMessage = msg;
        for (Agent agent : subs) {
            agent.callback(name, msg);
        }
    }

    public Message getLastMessage() {
        return lastMessage;
    }

    public Set<Agent> getSubscribers() {
        return Set.copyOf(subs);
    }

    public Set<Agent> getPublishers() {
        return Set.copyOf(pubs);
    }
}