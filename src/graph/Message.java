package graph;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * Immutable message value passed between topics and agents.
 * The public final fields match the assignment API and provide byte, text,
 * numeric, and timestamp views of the same message.
 */
public class Message {

    public final byte[] data;
    public final String asText;
    public final double asDouble;
    public final Date date;

    /**
     * Creates a message from text encoded as UTF-8.
     * Invalid numeric text is represented by {@link Double#NaN}.
     *
     * @param text message text
     */
    public Message(String text) {
        this.asText = text;
        this.data = text.getBytes(StandardCharsets.UTF_8);

        double parsedValue;
        try {
            parsedValue = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            parsedValue = Double.NaN;
        }

        this.asDouble = parsedValue;
        this.date = new Date();
    }

    /**
     * Creates a message from a numeric value.
     *
     * @param number numeric message value
     */
    public Message(double number) {
        this(String.valueOf(number));
    }

    /**
     * Creates a message from UTF-8 bytes. The provided array is copied.
     *
     * @param bytes message bytes
     */
    public Message(byte[] bytes) {
        this(new String(
                Arrays.copyOf(bytes, bytes.length),
                StandardCharsets.UTF_8
        ));
    }
}