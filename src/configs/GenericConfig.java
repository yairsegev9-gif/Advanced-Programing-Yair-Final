package configs;

import graph.Agent;
import graph.ParallelAgent;
import graph.TopicManagerSingleton;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic file-backed configuration loader.
 * A configuration file is read as groups of three lines: agent class name,
 * comma-separated subscription topics, and comma-separated publication topics.
 * Agents are constructed reflectively and wrapped in {@link graph.ParallelAgent}
 * so callbacks execute asynchronously.
 */
public class GenericConfig implements Config {
    private String confFile;
    private final List<LoadedAgent> agents = new ArrayList<>();

    /**
     * Sets the configuration file to load on {@link #create()}.
     *
     * @param confFile path to a UTF-8 configuration file
     */
    public void setConfFile(String confFile) {
        if (confFile == null || confFile.isBlank()) {
            throw new IllegalArgumentException("configuration file path cannot be blank");
        }
        this.confFile = confFile;
    }

    @Override
    public void create() {
        if (confFile == null) {
            throw new IllegalStateException("configuration file was not set");
        }

        close();
        List<String> lines = readNonBlankLines(confFile);
        if (lines.size() % 3 != 0) {
            throw new IllegalArgumentException("configuration file must contain groups of three non-blank lines");
        }

        try {
            for (int i = 0; i < lines.size(); i += 3) {
                String className = lines.get(i).trim();
                String[] subs = parseTopics(lines.get(i + 1));
                String[] pubs = parseTopics(lines.get(i + 2));
                Agent rawAgent = constructAgent(className, subs, pubs);
                rawAgent.close();
                ParallelAgent parallelAgent = new ParallelAgent(rawAgent, 100);
                for (String sub : subs) {
                    TopicManagerSingleton.get().getTopic(sub).subscribe(parallelAgent);
                }
                for (String pub : pubs) {
                    TopicManagerSingleton.get().getTopic(pub).addPublisher(parallelAgent);
                }
                agents.add(new LoadedAgent(parallelAgent, subs, pubs));
            }
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public String getName() {
        return "Generic Config";
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void close() {
        for (LoadedAgent loaded : agents) {
            for (String sub : loaded.subs) {
                TopicManagerSingleton.get().getTopic(sub).unsubscribe(loaded.agent);
            }
            for (String pub : loaded.pubs) {
                TopicManagerSingleton.get().getTopic(pub).removePublisher(loaded.agent);
            }
            loaded.agent.close();
        }
        agents.clear();
    }

    private static List<String> readNonBlankLines(String fileName) {
        try {
            List<String> result = new ArrayList<>();
            for (String line : Files.readAllLines(Path.of(fileName), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read configuration file: " + fileName, e);
        }
    }

    private static String[] parseTopics(String line) {
        String[] raw = line.split(",");
        List<String> topics = new ArrayList<>();
        for (String topic : raw) {
            String trimmed = topic.trim();
            if (!trimmed.isEmpty()) {
                topics.add(trimmed);
            }
        }
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("topic list cannot be empty");
        }
        return topics.toArray(new String[0]);
    }

    private static Agent constructAgent(String className, String[] subs, String[] pubs) {
        try {
            Class<?> type = Class.forName(className);
            if (!Agent.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(className + " does not implement Agent");
            }
            Constructor<?> constructor = type.getConstructor(String[].class, String[].class);
            return (Agent) constructor.newInstance(subs, pubs);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("agent class not found: " + className, e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("agent class is missing constructor(String[], String[]): " + className, e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("cannot construct agent: " + className, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalArgumentException("agent constructor failed: " + className, cause);
        }
    }

    private static class LoadedAgent {
        private final ParallelAgent agent;
        private final String[] subs;
        private final String[] pubs;

        private LoadedAgent(ParallelAgent agent, String[] subs, String[] pubs) {
            this.agent = agent;
            this.subs = subs.clone();
            this.pubs = pubs.clone();
        }
    }
}