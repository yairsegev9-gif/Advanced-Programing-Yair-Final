package configs;

/**
 * Creates and closes a complete computational graph configuration.
 */
public interface Config {
    void create();
    String getName();
    int getVersion();
    void close();
}