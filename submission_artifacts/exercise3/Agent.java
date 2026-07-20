package test;

/**
 * A processing unit in the publish/subscribe computational graph.
 * Agents subscribe to input topics, react to incoming messages, and may publish
 * derived messages to output topics.
 */
public interface Agent {
    /**
     * @return the human-readable agent name used in graph visualizations.
     */
    String getName();
    /**
     * Resets the agent internal state according to its concrete semantics.
     */
    void reset();
    /**
     * Handles a message published on a subscribed topic.
     *
     * @param topic the name of the topic that produced the message
     * @param msg the received immutable message
     */
    void callback(String topic, Message msg);
    /**
     * Releases registrations, threads, or other resources owned by the agent.
     */
    void close();
}
