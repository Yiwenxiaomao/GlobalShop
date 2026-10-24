package com.example.globalshop.feature.auction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every live auction, in the world save.
 *
 * <h2>Why the save file is the only place this can live</h2>
 * An auction holds two things that belong to somebody: an item that has left a player's inventory,
 * and money that has left a player's balance. Both must survive a restart, a crash included.
 * Anything kept in memory would mean that restarting the server during an auction either destroys
 * the goods or mints the money back, and both are worse than losing the auction.
 *
 * <h2>Held money is not a second balance</h2>
 * There is deliberately no "frozen balance" counter per player. The money of a bid has already left
 * {@code EconomyService}; what this class knows is which auction is holding how much, on whose
 * behalf ({@link AuctionListing#topBid()}). A separate per-player tally would be a second source of
 * truth for the same fact, and the two would eventually disagree — which is exactly the bug the
 * requirement's audit rule is trying to prevent. {@link #heldTotal()} is derived by summing the
 * auctions, and {@link #audit()} cross-checks the things that could still be inconsistent.
 */
public class AuctionSavedData extends SavedData {

    public static final String DATA_NAME = "globalshop_auctions";

    private static final String TAG_AUCTIONS = "Auctions";

    /** Keyed by auction id. Insertion order so a listing's own order is stable in the file. */
    private final Map<UUID, AuctionListing> auctions = new LinkedHashMap<>();

    public static AuctionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(AuctionSavedData::load, AuctionSavedData::new, DATA_NAME);
    }

    /**
     * Reads the stored copy back through a save/load round trip.
     *
     * <p>For the self test, which has to prove that a restart keeps an auction and the money it is
     * holding. Reloading from the live object exercises the same code a real restart would.
     */
    public static AuctionSavedData reload(MinecraftServer server) {
        return load(get(server).save(new CompoundTag()));
    }

    public static AuctionSavedData load(CompoundTag tag) {
        AuctionSavedData data = new AuctionSavedData();
        ListTag list = tag.getList(TAG_AUCTIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            AuctionListing listing = AuctionListing.load(list.getCompound(i));
            // A corrupt entry is skipped rather than crashing the server on load. It is logged by
            // the service when it notices the id is gone; silently dropping the whole file would be
            // far worse than losing one auction.
            if (listing != null) {
                data.auctions.put(listing.id(), listing);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AuctionListing listing : auctions.values()) {
            list.add(listing.save());
        }
        tag.put(TAG_AUCTIONS, list);
        return tag;
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /** Every auction still in the save, including ones whose goods have yet to be delivered. */
    public Collection<AuctionListing> all() {
        return new ArrayList<>(auctions.values());
    }

    /** Only the auctions still open for bids. */
    public List<AuctionListing> active() {
        List<AuctionListing> open = new ArrayList<>();
        for (AuctionListing listing : auctions.values()) {
            if (listing.state() == AuctionListing.State.ACTIVE) {
                open.add(listing);
            }
        }
        return open;
    }

    @Nullable
    public AuctionListing byId(UUID id) {
        return auctions.get(id);
    }

    public void put(AuctionListing listing) {
        auctions.put(listing.id(), listing);
        setDirty();
    }

    /** @return whether anything was there to remove */
    public boolean remove(UUID id) {
        if (auctions.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public int size() {
        return auctions.size();
    }

    /** How many auctions this player has open. Used for the per-player cap. */
    public int openCountFor(UUID sellerId) {
        int count = 0;
        for (AuctionListing listing : auctions.values()) {
            if (listing.state() == AuctionListing.State.ACTIVE && listing.sellerId().equals(sellerId)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    /**
     * All the money this auction house is holding on players' behalf.
     *
     * <p>Derived, never stored: the sum of every auction's held bid whose payout has not happened
     * yet. Storing it as well would create the second source of truth this class exists to avoid.
     */
    public long heldTotal() {
        long total = 0L;
        for (AuctionListing listing : auctions.values()) {
            total += heldBy(listing);
        }
        return total;
    }

    /** What one auction is holding right now. Zero once the seller has been paid. */
    public static long heldBy(AuctionListing listing) {
        if (!listing.hasBid()) {
            return 0L;
        }
        if (listing.state() == AuctionListing.State.EXPIRED) {
            // The hammer fell with no bids being honoured, so nothing is held.
            return 0L;
        }
        return listing.paidOut() ? 0L : listing.topBid();
    }

    /** What one player currently has tied up in auctions they are winning. */
    public long heldFor(UUID bidderId) {
        long total = 0L;
        for (AuctionListing listing : auctions.values()) {
            if (listing.hasBid() && bidderId.equals(listing.topBidderId())) {
                total += heldBy(listing);
            }
        }
        return total;
    }

    /**
     * Checks the invariants that must hold for the accounting to be sound.
     *
     * <p>Returns a list of complaints rather than a boolean so a failure says what was wrong. The
     * self test asserts this is empty after every scenario it runs, including the ones that refuse
     * bids — the requirement is that ``spendable + frozen`` never goes negative, and the cheapest
     * way to keep that true is to refuse to let these states exist at all.
     */
    public List<String> audit() {
        List<String> complaints = new ArrayList<>();
        for (AuctionListing listing : auctions.values()) {
            if (listing.hasBid() && listing.topBid() <= 0L) {
                complaints.add(listing.id() + ": has a bidder but no held money");
            }
            if (!listing.hasBid() && listing.topBid() != 0L) {
                complaints.add(listing.id() + ": holds money with no bidder");
            }
            if (listing.topBid() < 0L) {
                complaints.add(listing.id() + ": negative held money");
            }
            if (listing.state() == AuctionListing.State.SOLD && !listing.hasBid()) {
                complaints.add(listing.id() + ": sold with no bidder");
            }
            if (listing.state() == AuctionListing.State.EXPIRED && listing.hasBid()) {
                complaints.add(listing.id() + ": expired while holding a bid");
            }
            if (listing.item().isEmpty()) {
                complaints.add(listing.id() + ": holds no item");
            }
            if (listing.paidOut() && listing.state() != AuctionListing.State.SOLD) {
                complaints.add(listing.id() + ": paid out without a sale");
            }
        }
        return complaints;
    }
}
