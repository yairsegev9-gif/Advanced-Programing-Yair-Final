package configs;

import graph.Agent;
import graph.Message;
import graph.TopicManagerSingleton;

import java.util.function.BinaryOperator;

/**
 * Binary-operation agent that combines two numeric input topics and publishes
 * the result to one output topic.
 */
public class BinOpAgent implements Agent {

    private final String Agent_name;
    private final String Topic1_input;
    private final String Topic2_input;
    private final String Topic_output;
    private final BinaryOperator<Double> op;

    private double value1 = 0;
    private double value2 = 0;
    private boolean hasValue1 = false;
    private boolean hasValue2 = false;

    public BinOpAgent(
            String name,
            String topic1,
            String topic2,
            String outputTopic,
            BinaryOperator<Double> op
    ) {
        if (name == null || topic1 == null || topic2 == null || outputTopic == null || op == null) {
            throw new IllegalArgumentException("BinOpAgent arguments cannot be null");
        }

        this.Agent_name = name;
        this.Topic1_input = topic1;
        this.Topic2_input = topic2;
        this.Topic_output = outputTopic;
        this.op = op;

        TopicManagerSingleton.get()
                .getTopic(Topic1_input)
                .subscribe(this);

        TopicManagerSingleton.get()
                .getTopic(Topic2_input)
                .subscribe(this);

        TopicManagerSingleton.get()
                .getTopic(Topic_output)
                .addPublisher(this);
    }

    @Override
    public String getName() {
        return this.Agent_name;
    }

    @Override
    public void callback(String topic, Message msg) {
        if (topic == null || msg == null || Double.isNaN(msg.asDouble)) {
            return;
        }

        if (topic.equals(Topic1_input)) {
            this.value1 = msg.asDouble;
            this.hasValue1 = true;
        } else if (topic.equals(Topic2_input)) {
            this.value2 = msg.asDouble;
            this.hasValue2 = true;
        } else {
            return;
        }

        if (hasValue1 && hasValue2 && !Double.isNaN(value1) && !Double.isNaN(value2)) {
            double result = this.op.apply(value1, value2);
            if (!Double.isNaN(result)) {
                TopicManagerSingleton.get()
                        .getTopic(Topic_output)
                        .publish(new Message(result));
            }
        }
    }

    @Override
    public void reset() {
        this.value1 = 0;
        this.value2 = 0;
        this.hasValue1 = true;
        this.hasValue2 = true;
    }

    @Override
    public void close() {
        TopicManagerSingleton.get()
                .getTopic(Topic1_input)
                .unsubscribe(this);

        TopicManagerSingleton.get()
                .getTopic(Topic2_input)
                .unsubscribe(this);

        TopicManagerSingleton.get()
                .getTopic(Topic_output)
                .removePublisher(this);
    }
}