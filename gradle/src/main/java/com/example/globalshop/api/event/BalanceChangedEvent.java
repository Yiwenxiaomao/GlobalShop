package com.example.globalshop.api.event;

import java.util.UUID;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted after a balance changed, whoever changed it.
 *
 * <p>Every route through the economy fires this: the market, the bank, a command, and an addon that
 * calls {@code EconomyService} itself. An addon that wants to react to money moving — a scoreboard,
 * a log, a title that depends on wealth — listens here rather than polling.
 *
 * <p>Not cancelable. The money has already moved; a listener that disagrees with it should move it
 * back, which is a decision with consequences and should look like one.
 *
 * <p>The two amounts are the balance before and after, so a listener does not have to guess the
 * direction or the size from a delta that might be zero either way.
 *
 * @since 1.1.0
 */
public class BalanceChangedEvent extends Event {

    private final UUID playerId;
    private final long before;
    private final long after;
    private final String reason;

    /**
     * @param reason a short, stable word for what caused it — {@code "auction"}, {@code "bank"},
     *               {@code "transfer"} — or an empty string when the caller did not say. For logs
     *               and for a listener that only cares about one kind of change.
     */
    public BalanceChangedEvent(UUID playerId, long before, long after, String reason) {
        this.playerId = playerId;
        this.before = before;
        this.after = after;
        this.reason = reason == null ? "" : reason;
    }

    public UUID playerId() {
        return playerId;
    }

    public long balanceBefore() {
        return before;
    }

    public long balanceAfter() {
        return after;
    }

    /** What changed it, or an empty string. Never null. */
    public String reason() {
        return reason;
    }

    /** How much was added, or how much was removed when this is negative. */
    public long delta() {
        return after - before;
    }
}
