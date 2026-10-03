package com.example.globalshop.shop;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * One delivery waiting in a player's mailbox.
 *
 * <h2>Why a mail is a delivery, not a stack</h2>
 * A single purchase can be more than one stack — 128 stone is two — and the player thinks of that
 * as one thing arriving. So a mail holds the whole delivery, and the mailbox's limit counts
 * deliveries rather than stacks. A player with 100 unclaimed purchases has a real backlog; a
 * player with 100 unclaimed stacks may only have three.
 *
 * <h2>Why {@link #createdAt} is never reset</h2>
 * When part of a delivery will not fit, the remainder is written back with its <em>original</em>
 * creation time. Restamping it would let a player keep a mail alive forever by repeatedly taking
 * some of it and leaving the rest, which is exactly what an expiry is supposed to prevent.
 */
public final class Mail {

    private static final String TAG_ID = "Id";
    private static final String TAG_CREATED = "Created";
    private static final String TAG_SOURCE = "Source";
    private static final String TAG_ITEMS = "Items";

    /** Where a delivery came from, so the mailbox can say who sent it. */
    public enum Source {
        /** A purchase from another player, or stock returned when a lot was taken down. */
        MARKET("globalshop.mail.source.market"),
        /** Bought from, or sold to, the server's own shop. */
        SERVER_SHOP("globalshop.mail.source.server_shop");

        private final String key;

        Source(String key) {
            this.key = key;
        }

        public String translationKey() {
            return key;
        }

        @Nullable
        public static Source byName(String name) {
            for (Source source : values()) {
                if (source.name().equals(name)) {
                    return source;
                }
            }
            return null;
        }
    }

    private final long id;
    private final long createdAt;
    private final Source source;
    private final List<ItemStack> items;

    public Mail(long id, long createdAt, Source source, List<ItemStack> items) {
        this.id = id;
        this.createdAt = createdAt;
        this.source = source;
        List<ItemStack> kept = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                kept.add(stack.copy());
            }
        }
        this.items = List.copyOf(kept);
    }

    public long id() {
        return id;
    }

    public long createdAt() {
        return createdAt;
    }

    public Source source() {
        return source;
    }

    /** The delivery. Immutable; every stack is a copy the caller may not mutate into the mail. */
    public List<ItemStack> items() {
        List<ItemStack> copies = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copies.add(stack.copy());
        }
        return copies;
    }

    /** How many items in total, for a one-line summary. */
    public int totalCount() {
        int total = 0;
        for (ItemStack stack : items) {
            total += stack.getCount();
        }
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** When this mail stops being claimable, in epoch milliseconds. */
    public long expiresAt() {
        return createdAt + com.example.globalshop.Config.mailLifetimeMillis();
    }

    /**
     * Whether this mail has timed out.
     *
     * <p>Measured against the shop's own clock rather than game time, so a mail expires while the
     * server is switched off — a player who comes back after a fortnight finds it lapsed, which is
     * what "14 天" means to them.
     */
    public boolean isExpired(long now) {
        return now >= expiresAt();
    }

    /** A copy with different contents, keeping the id and — crucially — the creation time. */
    public Mail withItems(List<ItemStack> replacement) {
        return new Mail(id, createdAt, source, replacement);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_ID, id);
        tag.putLong(TAG_CREATED, createdAt);
        tag.putString(TAG_SOURCE, source.name());
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put(TAG_ITEMS, list);
        return tag;
    }

    /** @return the mail, or {@code null} if the tag does not describe one worth keeping */
    @Nullable
    public static Mail load(CompoundTag tag) {
        ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        List<ItemStack> items = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        Source source = Source.byName(tag.getString(TAG_SOURCE));
        return new Mail(tag.getLong(TAG_ID), tag.getLong(TAG_CREATED),
                source == null ? Source.MARKET : source, items);
    }
}
