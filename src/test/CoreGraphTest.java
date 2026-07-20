package test;

import graph.Agent;
import graph.Message;
import graph.ParallelAgent;
import graph.Topic;
import graph.TopicManagerSingleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CoreGraphTest {

    public static void main(String[] args) throws Exception {
        testMessageConstructors();
        testTopicSubscribePublishAndPublisherRegistration();
        testTopicManagerUniquenessAndClear();
        testParallelAgentOrderingAndReset();
        testParallelAgentCloseLifecycle();
        testParallelAgentSurvivesWrappedAgentException();
        System.out.println("CoreGraphTest passed");
    }

    private static void testMessageConstructors() {
        Message text = new Message("12.5");
        assertEquals("12.5", text.asText, "text constructor should preserve text");
        assertEquals(12.5, text.asDouble, "text constructor should parse double");
        assertEquals("12.5", new String(text.data, StandardCharsets.UTF_8), "text constructor should store UTF-8 bytes");
        assertTrue(text.date != null, "message should have a creation date");

        Message unicode = new Message("????");
        assertEquals("????", unicode.asText, "UTF-8 text should round-trip");

        Message invalid = new Message("not-a-number");
        assertTrue(Double.isNaN(invalid.asDouble), "invalid numeric text should produce NaN");

        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        Message fromBytes = new Message(bytes);
        bytes[0] = 'z';
        assertEquals("abc", fromBytes.asText, "byte[] constructor should copy input data");

        Message number = new Message(4.25);
        assertEquals(4.25, number.asDouble, "double constructor should store numeric value");
    }

    private static void testTopicSubscribePublishAndPublisherRegistration() {
        TopicManagerSingleton.get().clear();
        Topic topic = TopicManagerSingleton.get().getTopic("events");
        RecordingAgent sub = new RecordingAgent("sub");
        RecordingAgent pub = new RecordingAgent("pub");

        topic.subscribe(sub);
        topic.subscribe(sub);
        topic.publish(new Message("one"));
        assertEquals(1, sub.events.size(), "duplicate subscription should not duplicate delivery");

        topic.unsubscribe(sub);
        topic.publish(new Message("two"));
        assertEquals(1, sub.events.size(), "unsubscribe should stop delivery");

        topic.addPublisher(pub);
        topic.addPublisher(pub);
        assertEquals(1, topic.getPublishers().size(), "duplicate publisher registration should collapse");
        topic.removePublisher(pub);
        assertEquals(0, topic.getPublishers().size(), "publisher removal should unregister agent");

        TopicManagerSingleton.get().clear();
    }

    private static void testTopicManagerUniquenessAndClear() {
        TopicManagerSingleton.get().clear();
        Topic a = TopicManagerSingleton.get().getTopic("same");
        Topic b = TopicManagerSingleton.get().getTopic("same");
        assertTrue(a == b, "getTopic should return the same topic instance for the same name");
        assertEquals(1, TopicManagerSingleton.get().getTopics().size(), "manager should contain one topic");
        TopicManagerSingleton.get().clear();
        assertEquals(0, TopicManagerSingleton.get().getTopics().size(), "clear should remove all topics");
    }

    private static void testParallelAgentOrderingAndReset() throws Exception {
        RecordingAgent base = new RecordingAgent("parallel");
        ParallelAgent parallel = new ParallelAgent(base, 4);

        parallel.callback("a", new Message("1"));
        parallel.callback("b", new Message("2"));
        parallel.reset();

        assertTrue(base.awaitEvents(3, 2000), "parallel agent should process callbacks and reset");
        assertEquals("callback:a:1", base.events.get(0), "first callback should run first");
        assertEquals("callback:b:2", base.events.get(1), "second callback should run second");
        assertEquals("reset", base.events.get(2), "reset should preserve queue order");

        parallel.close();
        assertTrue(base.closed, "close should close wrapped agent");
    }

    private static void testParallelAgentCloseLifecycle() throws Exception {
        BlockingAgent base = new BlockingAgent("blocked");
        ParallelAgent parallel = new ParallelAgent(base, 1);

        parallel.callback("hold", new Message("1"));
        assertTrue(base.started.await(1000, TimeUnit.MILLISECONDS), "first callback should start");
        parallel.callback("queued", new Message("2"));
        parallel.close();
        parallel.close();
        parallel.callback("ignored", new Message("3"));
        parallel.reset();
        base.release.countDown();

        assertTrue(base.closedLatch.await(2500, TimeUnit.MILLISECONDS), "close should eventually close wrapped agent");
        assertFalse(hasThreadNamed("ParallelAgent-blocked"), "parallel worker thread should not leak after close");
    }


    private static void testParallelAgentSurvivesWrappedAgentException() throws Exception {
        ThrowingRecordingAgent base = new ThrowingRecordingAgent("throws-once");
        ParallelAgent parallel = new ParallelAgent(base, 4);
        parallel.callback("topic", new Message("bad"));
        Thread.sleep(100);
        parallel.callback("topic", new Message("good"));
        assertTrue(base.awaitEvents(1, 2000), "worker should continue after wrapped callback throws");
        assertEquals("callback:topic:good", base.events.get(0), "second callback should be processed after exception");
        parallel.close();
    }
    private static boolean hasThreadNamed(String name) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals(name) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
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

    private static class RecordingAgent implements Agent {
        private final String name;
        protected final List<String> events = new ArrayList<>();
        private volatile boolean closed;

        protected RecordingAgent(String name) {
            this.name = name;
        }

        boolean awaitEvents(int count, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                synchronized (events) {
                    if (events.size() >= count) {
                        return true;
                    }
                }
                Thread.sleep(10);
            }
            synchronized (events) {
                return events.size() >= count;
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void reset() {
            synchronized (events) {
                events.add("reset");
            }
        }

        @Override
        public void callback(String topic, Message msg) {
            synchronized (events) {
                events.add("callback:" + topic + ":" + msg.asText);
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }


    private static class ThrowingRecordingAgent extends RecordingAgent {
        private boolean first = true;

        private ThrowingRecordingAgent(String name) {
            super(name);
        }

        @Override
        public void callback(String topic, Message msg) {
            if (first) {
                first = false;
                throw new RuntimeException("expected test exception");
            }
            super.callback(topic, msg);
        }
    }
    private static class BlockingAgent implements Agent {
        private final String name;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch closedLatch = new CountDownLatch(1);

        private BlockingAgent(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void reset() {
        }

        @Override
        public void callback(String topic, Message msg) {
            started.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            closedLatch.countDown();
        }
    }
}