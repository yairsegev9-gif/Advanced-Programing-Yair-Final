package test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

public class Message {
    public final byte[] data;
    public final String asText;
    public final double asDouble;
    public final Date date;

    public Message(String text) {
        asText = text;
        data = text.getBytes(StandardCharsets.UTF_8);

        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            value = Double.NaN;
        }

        asDouble = value;
        date = new Date();
    }

    public Message(double number) {
        this(String.valueOf(number));
    }

    public Message(byte[] bytes) {
        this(new String(
                Arrays.copyOf(bytes, bytes.length),
                StandardCharsets.UTF_8
        ));
    }
}