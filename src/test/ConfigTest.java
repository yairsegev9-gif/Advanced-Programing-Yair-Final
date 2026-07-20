package test;

import configs.GenericConfig;
import configs.MathExampleConfig;
import configs.PlusAgent;
import graph.Agent;
import graph.Message;
import graph.TopicManagerSingleton;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigTest {

    public static void main(String[] args) throws Exception {
        testPlusAndIncAgentsDirectly();
        testMathExampleConfig();
        testGenericConfigValidFile();
        testGenericConfigMalformedFiles();
        testGenericConfigCloseUnregistersAndStopsWorkers();
        System.out.println("ConfigTest passed");
    }

    private static void testPlusAndIncAgentsDirectly() {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("D");
        PlusAgent plus = new PlusAgent(new String[]{"A", "B"}, new String[]{"C"});
        configs.IncAgent inc = new configs.IncAgent(new String[]{"C"}, new String[]{"D"});

        TopicManagerSingleton.get().getTopic("A").publish(new Message(4.0));
        TopicManagerSingleton.get().getTopic("B").publish(new Message(6.0));
        assertDoubles(Arrays.asList(5.0, 11.0), capture.values, "plus should use default zero, then plus and inc should publish 11");

        plus.reset();
        TopicManagerSingleton.get().getTopic("A").publish(new Message(2.0));
        assertDoubles(Arrays.asList(5.0, 11.0, 3.0), capture.values, "reset should make other plus input zero");

        plus.close();
        inc.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testMathExampleConfig() {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("R3");
        MathExampleConfig config = new MathExampleConfig();
        config.create();

        TopicManagerSingleton.get().getTopic("A").publish(new Message(5.0));
        TopicManagerSingleton.get().getTopic("B").publish(new Message(2.0));
        assertDoubles(Arrays.asList(21.0), capture.values, "(5+2)*(5-2) should publish 21");

        config.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testGenericConfigValidFile() throws Exception {
        TopicManagerSingleton.get().clear();
        CaptureAgent capture = subscribeCapture("D");
        Path file = writeConfig("configs.PlusAgent\nA,B\nC\nconfigs.IncAgent\nC\nD\n");
        GenericConfig config = new GenericConfig();
        config.setConfFile(file.toString());
        config.create();

        TopicManagerSingleton.get().getTopic("A").publish(new Message(7.0));
        TopicManagerSingleton.get().getTopic("B").publish(new Message(8.0));
        assertTrue(capture.awaitValues(1, 2000), "generic config should publish through parallel agents");
        assertDoubles(Arrays.asList(8.0, 16.0), capture.values, "generic config should use default zero and then run plus then inc");

        config.close();
        TopicManagerSingleton.get().clear();
    }

    private static void testGenericConfigMalformedFiles() throws Exception {
        expectFailure(() -> new GenericConfig().setConfFile(""), "blank configuration file path should fail");

        GenericConfig missing = new GenericConfig();
        missing.setConfFile("missing-file-that-should-not-exist.conf");
        expectFailure(missing::create, "missing file should fail");

        GenericConfig wrongLines = new GenericConfig();
        wrongLines.setConfFile(writeConfig("configs.PlusAgent\nA,B\n").toString());
        expectFailure(wrongLines::create, "wrong number of lines should fail");

        GenericConfig missingClass = new GenericConfig();
        missingClass.setConfFile(writeConfig("missing.Nope\nA\nB\n").toString());
        expectFailure(missingClass::create, "missing class should fail");

        GenericConfig notAgent = new GenericConfig();
        notAgent.setConfFile(writeConfig("java.lang.String\nA\nB\n").toString());
        expectFailure(notAgent::create, "class not implementing Agent should fail");

        GenericConfig missingCtor = new GenericConfig();
        missingCtor.setConfFile(writeConfig("test.ConfigTest$NoConstructorAgent\nA\nB\n").toString());
        expectFailure(missingCtor::create, "missing required constructor should fail");

        GenericConfig badTopics = new GenericConfig();
        badTopics.setConfFile(writeConfig("configs.IncAgent\n,\nB\n").toString());
        expectFailure(badTopics::create, "empty topic list should fail");

        TopicManagerSingleton.get().clear();
    }

    private static void testGenericConfigCloseUnregistersAndStopsWorkers() throws Exception {
        TopicManagerSingleton.get().clear();
        Path file = writeConfig("configs.IncAgent\nA\nB\n");
        GenericConfig config = new GenericConfig();
        config.setConfFile(file.toString());
        config.create();

        assertEquals(1, TopicManagerSingleton.get().getTopic("A").getSubscribers().size(), "wrapped agent should subscribe");
        assertEquals(1, TopicManagerSingleton.get().getTopic("B").getPublishers().size(), "wrapped agent should publish");

        config.close();
        config.close();
        assertEquals(0, TopicManagerSingleton.get().getTopic("A").getSubscribers().size(), "close should remove subscriptions");
        assertEquals(0, TopicManagerSingleton.get().getTopic("B").getPublishers().size(), "close should remove publishers");
        Thread.sleep(50);
        assertFalse(hasThreadPrefix("ParallelAgent-IncAgent"), "generic config should not leak workers");
        TopicManagerSingleton.get().clear();
    }

    private static Path writeConfig(String content) throws Exception {
        Path file = Files.createTempFile("advanced-programming-config", ".conf");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static CaptureAgent subscribeCapture(String topic) {
        CaptureAgent capture = new CaptureAgent("capture-" + topic);
        TopicManagerSingleton.get().getTopic(topic).subscribe(capture);
        return capture;
    }

    private static boolean hasThreadPrefix(String prefix) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith(prefix) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void assertDoubles(List<Double> expected, List<Double> actual, String message) {
        assertEquals(expected.size(), actual.size(), message + " size");
        for (int i = 0; i < expected.size(); i++) {
            if (Math.abs(expected.get(i) - actual.get(i)) > 0.000001) {
                throw new AssertionError(message + " expected " + expected + " but got " + actual);
            }
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
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

    public static class NoConstructorAgent implements Agent {
        @Override
        public String getName() {
            return "NoConstructorAgent";
        }

        @Override
        public void reset() {
        }

        @Override
        public void callback(String topic, Message msg) {
        }

        @Override
        public void close() {
        }
    }

    private static class CaptureAgent implements Agent {
        private final String name;
        private final List<Double> values = new ArrayList<>();

        private CaptureAgent(String name) {
            this.name = name;
        }

        boolean awaitValues(int count, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                synchronized (values) {
                    if (values.size() >= count) {
                        return true;
                    }
                }
                Thread.sleep(10);
            }
            synchronized (values) {
                return values.size() >= count;
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void reset() {
            values.clear();
        }

        @Override
        public void callback(String topic, Message msg) {
            synchronized (values) {
                values.add(msg.asDouble);
            }
        }

        @Override
        public void close() {
            values.clear();
        }
    }
}