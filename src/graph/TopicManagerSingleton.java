package graph;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class TopicManagerSingleton {
    public static class TopicManager {
        private static final TopicManager instance = new TopicManager();
        private final ConcurrentHashMap<String, Topic> topics = new ConcurrentHashMap<>();
        private TopicManager() {}

        public Topic getTopic(String name){
            return topics.computeIfAbsent(name,k -> new Topic(k));
        }
        public Collection<Topic> getTopics(){
            return topics.values();
        }
        public void clear(){
            topics.clear();
        }


    }
    public static TopicManager get(){
        return TopicManager.instance;
    }
}
