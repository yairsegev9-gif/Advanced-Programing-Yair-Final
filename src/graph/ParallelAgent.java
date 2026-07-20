package graph;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ParallelAgent implements Agent {

    private final Agent agent;
    private final BlockingQueue<Task> queue;
    private final Thread workerThread;
    private final Object lifecycleLock = new Object();

    private volatile boolean closed = false;

    private enum TaskType {
        CALLBACK,
        RESET,
        SHUTDOWN
    }

    private static class Task {
        final TaskType type;
        final String topic;
        final Message msg;

        private Task(TaskType type, String topic, Message msg) {
            this.type = type;
            this.topic = topic;
            this.msg = msg;
        }

        static Task callback(String topic, Message msg) {
            return new Task(TaskType.CALLBACK, topic, msg);
        }

        static Task reset() {
            return new Task(TaskType.RESET, null, null);
        }

        static Task shutdown() {
            return new Task(TaskType.SHUTDOWN, null, null);
        }
    }

    public ParallelAgent(Agent agent, int capacity) {
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        this.agent = agent;
        this.queue = new ArrayBlockingQueue<>(capacity);

        this.workerThread = new Thread(() -> {
            try {
                while (true) {
                    Task task = queue.take();

                    switch (task.type) {
                        case CALLBACK:
                            try {
                                agent.callback(task.topic, task.msg);
                            } catch (RuntimeException ignored) {
                            }
                            break;

                        case RESET:
                            try {
                                agent.reset();
                            } catch (RuntimeException ignored) {
                            }
                            break;

                        case SHUTDOWN:
                            return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                agent.close();
            }
        }, "ParallelAgent-" + agent.getName());

        this.workerThread.start();
    }

    @Override
    public void callback(String topic, Message msg) {
        if (closed) {
            return;
        }

        putTask(Task.callback(topic, msg));
    }

    @Override
    public String getName() {
        return agent.getName();
    }

    @Override
    public void reset() {
        if (closed) {
            return;
        }

        putTask(Task.reset());
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }

            closed = true;
            if (!queue.offer(Task.shutdown())) {
                queue.clear();
                queue.offer(Task.shutdown());
            }
            workerThread.interrupt();
        }

        try {
            workerThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void putTask(Task task) {
        try {
            while (!closed) {
                if (queue.offer(task, 100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}