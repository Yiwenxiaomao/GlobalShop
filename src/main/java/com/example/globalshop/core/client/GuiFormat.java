package com.example.globalshop.core.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small shared date/time rendering for the client screens. */
public final class GuiFormat {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private GuiFormat() {
    }

    /**
     * A trade's timestamp as "MM-dd HH:mm".
     *
     * <p>Uses the client's own zone, which is right for a screen only the local player reads:
     * a time that matched the server's zone instead would look wrong to them.
     */
    public static String shortTime(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis));
    }
}
