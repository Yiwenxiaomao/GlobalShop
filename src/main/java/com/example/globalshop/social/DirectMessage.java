package com.example.globalshop.social;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * One private message between two players.
 *
 * <h2>Why both directions live in one record</h2>
 * A conversation is addressed by a <em>pair</em> of players, not by an owner. Storing each message
 * once, with a sender and a recipient, means a conversation is a filter over one log rather than two
 * inboxes that have to be kept in step — and it is what makes "show me the messages between me and
 * them" a question the storage can answer directly.
 *
 * <h2>What {@code delivered} means</h2>
 * Not "the recipient read it" — there is no read receipt and inventing one would be a lie. It means
 * <em>the recipient has been told they have unread messages</em>. That is what the login notice is,
 * and it must happen once: a player who logs in five times should not be told five times.
 */
public final class DirectMessage {

    private static final String TAG_ID = "Id";
    private static final String TAG_FROM = "From";
    private static final String TAG_FROM_NAME = "FromName";
    private static final String TAG_TO = "To";
    private static final String TAG_TEXT = "Text";
    private static final String TAG_TIME = "Time";
    private static final String TAG_DELIVERED = "Delivered";

    /** Longest message the server will store. Longer input is refused, not truncated silently. */
    public static final int MAX_LENGTH = 200;

    private final long id;
    private final UUID from;
    private final String fromName;
    private final UUID to;
    private final String text;
    private final long time;
    private boolean delivered;

    public DirectMessage(long id, UUID from, String fromName, UUID to, String text, long time,
                         boolean delivered) {
        this.id = id;
        this.from = from;
        this.fromName = fromName;
        this.to = to;
        this.text = text;
        this.time = time;
        this.delivered = delivered;
    }

    public long id() {
        return id;
    }

    public UUID from() {
        return from;
    }

    public String fromName() {
        return fromName;
    }

    public UUID to() {
        return to;
    }

    public String text() {
        return text;
    }

    public long time() {
        return time;
    }

    public boolean delivered() {
        return delivered;
    }

    void markDelivered() {
        this.delivered = true;
    }

    /** Whether this message is part of the conversation between these two players. */
    public boolean isBetween(UUID a, UUID b) {
        return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
    }

    /** Whether this is the kind of text worth keeping: non-blank and within the limit. */
    public static boolean isAcceptable(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return !trimmed.isEmpty() && trimmed.length() <= MAX_LENGTH;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_ID, id);
        tag.putUUID(TAG_FROM, from);
        tag.putString(TAG_FROM_NAME, fromName == null ? "" : fromName);
        tag.putUUID(TAG_TO, to);
        tag.putString(TAG_TEXT, text);
        tag.putLong(TAG_TIME, time);
        tag.putBoolean(TAG_DELIVERED, delivered);
        return tag;
    }

    @Nullable
    public static DirectMessage load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_FROM) || !tag.hasUUID(TAG_TO)) {
            return null;
        }
        String text = tag.getString(TAG_TEXT);
        if (!isAcceptable(text)) {
            return null;
        }
        return new DirectMessage(tag.getLong(TAG_ID), tag.getUUID(TAG_FROM),
                tag.getString(TAG_FROM_NAME), tag.getUUID(TAG_TO), text, tag.getLong(TAG_TIME),
                tag.getBoolean(TAG_DELIVERED));
    }
}
