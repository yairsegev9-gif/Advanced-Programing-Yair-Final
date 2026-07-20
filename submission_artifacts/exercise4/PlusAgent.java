package test;


public class PlusAgent implements Agent {
    private final String[] subs;
    private final String[] pubs;
    private double x = 0;
    private double y = 0;
    private boolean hasX = true;
    private boolean hasY = true;

    public PlusAgent(String[] subs, String[] pubs) {
        if (subs == null || pubs == null || subs.length < 2 || pubs.length < 1) {
            throw new IllegalArgumentException("PlusAgent requires two subscriptions and one publication topic");
        }
        this.subs = subs.clone();
        this.pubs = pubs.clone();
        TopicManagerSingleton.get().getTopic(this.subs[0]).subscribe(this);
        TopicManagerSingleton.get().getTopic(this.subs[1]).subscribe(this);
        TopicManagerSingleton.get().getTopic(this.pubs[0]).addPublisher(this);
    }

    @Override
    public String getName() {
        return "PlusAgent";
    }

    @Override
    public void reset() {
        x = 0;
        y = 0;
        hasX = true;
        hasY = true;
    }

    @Override
    public void callback(String topic, Message msg) {
        if (topic == null || msg == null || Double.isNaN(msg.asDouble)) {
            return;
        }
        if (topic.equals(subs[0])) {
            x = msg.asDouble;
            hasX = true;
        } else if (topic.equals(subs[1])) {
            y = msg.asDouble;
            hasY = true;
        } else {
            return;
        }
        if (hasX && hasY) {
            TopicManagerSingleton.get().getTopic(pubs[0]).publish(new Message(x + y));
        }
    }

    @Override
    public void close() {
        TopicManagerSingleton.get().getTopic(subs[0]).unsubscribe(this);
        TopicManagerSingleton.get().getTopic(subs[1]).unsubscribe(this);
        TopicManagerSingleton.get().getTopic(pubs[0]).removePublisher(this);
    }
}