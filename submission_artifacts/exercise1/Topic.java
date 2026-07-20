package test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Topic {
    public final String name;

    private final Set<Agent> subs;
    private final Set<Agent> pubs;

    Topic(String name) {
        this.name = name;
        subs = ConcurrentHashMap.newKeySet();
        pubs = ConcurrentHashMap.newKeySet();
    }

    public void subscribe(Agent agent) {
        subs.add(agent);
    }

    public void unsubscribe(Agent agent) {
        subs.remove(agent);
    }

    public void addPublisher(Agent agent) {
        pubs.add(agent);
    }

    public void removePublisher(Agent agent) {
        pubs.remove(agent);
    }

    public void publish(Message msg) {
        for (Agent agent : subs) {
            agent.callback(name, msg);
        }
    }
}