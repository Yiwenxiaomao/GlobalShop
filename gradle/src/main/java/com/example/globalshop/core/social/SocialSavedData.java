package com.example.globalshop.core.social;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side persistence for private messages.
 *
 * <p>One bounded log of every message, oldest first, rather than an inbox per player. A
 * conversation is a filter over this log, so there is no way for two inboxes to disagree about what
 * was said — and no way for a message to exist in one and not the other.
 *
 * <p>Like the rest of this mod's storage it is authoritative and lives in the world save, so a
 * restart cannot lose a message that a player was promised would be waiting for them.
 */
public final class SocialSavedData extends SavedData {

    public static final String DATA_NAME = "globalshop_social";

    private static final String TAG_LOG = "Log";
    private static final String TAG_NEXT_ID = "NextId";
    private static final String TAG_MESSAGE = "Message";
    private static final String TAG_OFFERS = "Offers";

    private final Deque<DirectMessage> log = new ArrayDeque<>();

    /**
     * Items offered for sale inside conversations, with the money still to change hands.
     *
     * <p>A list rather than a map: an offer is found by the pair of players involved and there can be
     * at most one per pair, so a list is both simpler and honest about the size of the thing. The
     * offered items are held here, so this is the only copy that matters.
     */
    private final List<TradeOffer> offers = new ArrayList<>();

    private long nextId = 1;

    public SocialSavedData() {
    }

    public static SocialSavedData load(CompoundTag tag) {
        SocialSavedData data = new SocialSavedData();
        ListTag messages = tag.getList(TAG_LOG, Tag.TAG_COMPOUND);
        for (int i = 0; i < messages.size(); i++) {
            DirectMessage message = DirectMessage.load(messages.getCompound(i));
            if (message != null) {
                data.log.addLast(message);
            }
        }
        data.nextId = Math.max(1L, tag.getLong(TAG_NEXT_ID));

        ListTag offerTags = tag.getList(TAG_OFFERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < offerTags.size(); i++) {
            TradeOffer offer = TradeOffer.load(offerTags.getCompound(i));
            if (offer != null) {
                data.offers.add(offer);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag messages = new ListTag();
        for (DirectMessage message : log) {
            messages.add(message.save());
        }
        tag.put(TAG_LOG, messages);
        tag.putLong(TAG_NEXT_ID, nextId);

        ListTag offerTags = new ListTag();
        for (TradeOffer offer : offers) {
            offerTags.add(offer.save());
        }
        tag.put(TAG_OFFERS, offerTags);
        return tag;
    }

    // ------------------------------------------------------------------
    // Offers
    // ------------------------------------------------------------------

    /** The offer between these two players, or {@code null}. At most one per pair. */
    @Nullable
    TradeOffer findOffer(UUID a, UUID b) {
        for (TradeOffer offer : offers) {
            if (offer.isBetween(a, b)) {
                return offer;
            }
        }
        return null;
    }

    /** Every offer one player is part of, in either direction. */
    List<TradeOffer> offersOf(UUID playerId) {
        List<TradeOffer> mine = new ArrayList<>();
        for (TradeOffer offer : offers) {
            if (offer.involves(playerId)) {
                mine.add(offer);
            }
        }
        return mine;
    }

    void putOffer(TradeOffer offer) {
        offers.removeIf(existing -> existing.isBetween(offer.sellerId(), offer.buyerId()));
        offers.add(offer);
    }

    /** @return the removed offer, or {@code null} if the pair had none */
    @Nullable
    TradeOffer removeOffer(UUID a, UUID b) {
        TradeOffer found = findOffer(a, b);
        if (found != null) {
            offers.remove(found);
        }
        return found;
    }

    /** Number of offers on file. Diagnostics and tests. */
    public int offerCount() {
        return offers.size();
    }

    /** Always the overworld, so every dimension shares one conversation history. */
    public static SocialSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(SocialSavedData::load, SocialSavedData::new, DATA_NAME);
    }

    long nextId() {
        return nextId++;
    }

    /** Adds a message, trimming the oldest once the log is longer than the retention setting. */
    void append(DirectMessage message, int retention) {
        log.addLast(message);
        if (retention > 0) {
            while (log.size() > retention) {
                log.removeFirst();
            }
        }
    }

    /** The most recent messages between two players, newest first. */
    List<DirectMessage> conversation(UUID a, UUID b, int limit) {
        List<DirectMessage> found = new ArrayList<>();
        java.util.Iterator<DirectMessage> newestFirst = log.descendingIterator();
        while (newestFirst.hasNext() && found.size() < limit) {
            DirectMessage message = newestFirst.next();
            if (message.isBetween(a, b)) {
                found.add(message);
            }
        }
        return found;
    }

    /** Messages this player has not yet been told about, oldest first. */
    List<DirectMessage> unnotified(UUID playerId) {
        List<DirectMessage> waiting = new ArrayList<>();
        for (DirectMessage message : log) {
            if (message.to().equals(playerId) && !message.delivered()) {
                waiting.add(message);
            }
        }
        return waiting;
    }

    /** Messages on file. Used by diagnostics and the self test. */
    public int logSize() {
        return log.size();
    }

    /** Serialises and re-reads, exactly as a restart does. Used by the self test. */
    public static SocialSavedData reload(SocialSavedData existing) {
        return load(existing.save(new CompoundTag()));
    }
}
