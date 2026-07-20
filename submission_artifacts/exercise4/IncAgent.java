package test;


public class IncAgent implements Agent {
    private final String[] subs;
    private final String[] pubs;

    public IncAgent(String[] subs, String[] pubs) {
        if (subs == null || pubs == null || subs.length < 1 || pubs.length < 1) {
            throw new IllegalArgumentException("IncAgent requires one subscription and one publication topic");
        }
        this.subs = subs.clone();
        this.pubs = pubs.clone();
        TopicManagerSingleton.get().getTopic(this.subs[0]).subscribe(this);
        TopicManagerSingleton.get().getTopic(this.pubs[0]).addPublisher(this);
    }

    @Override
    public String getName() {
        return "IncAgent";
    }

    @Override
    public void reset() {
    }

    @Override
    public void callback(String topic, Message msg) {
        if (topic == null || msg == null || !topic.equals(subs[0]) || Double.isNaN(msg.asDouble)) {
            return;
        }
        TopicManagerSingleton.get().getTopic(pubs[0]).publish(new Message(msg.asDouble + 1));
    }

    @Override
    public void close() {
        TopicManagerSingleton.get().getTopic(subs[0]).unsubscribe(this);
        TopicManagerSingleton.get().getTopic(pubs[0]).removePublisher(this);
    }
}