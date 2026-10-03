package com.example.globalshop.shop;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Told to a seller after one of their lots sells: what went, and what they were paid.
 *
 * <h2>Why it is stored rather than just sent</h2>
 * A seller is often offline when their lot sells. The requirement is that they still find out,
 * with the amount they actually received, the next time they log in. So the notice is written
 * into the world save beside the balance it describes, and delivered on login — the same
 * reasoning as the item mailbox next to it, for the same reason.
 *
 * <p>{@link #net} is the figure that matters to the seller and is always shown: the shop takes
 * its commission out of the total, so the number a seller cares about is what is left, not what
 * the buyer paid.
 */
public final class SaleNotice {

    private static final String TAG_SEQ = "Seq";
    private static final String TAG_TIME = "Time";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_QUANTITY = "Quantity";
    private static final String TAG_GROSS = "Gross";
    private static final String TAG_COMMISSION = "Commission";
    private static final String TAG_NET = "Net";
    private static final String TAG_BUYER = "Buyer";

    private final long seq;
    private final long time;
    private final String itemName;
    private final int quantity;
    private final long gross;
    private final long commission;
    private final long net;
    private final String buyerName;

    public SaleNotice(long seq, long time, String itemName, int quantity, long gross,
                      long commission, long net, String buyerName) {
        this.seq = seq;
        this.time = time;
        this.itemName = itemName;
        this.quantity = quantity;
        this.gross = gross;
        this.commission = commission;
        this.net = net;
        this.buyerName = buyerName;
    }

    public long seq() {
        return seq;
    }

    public long time() {
        return time;
    }

    public String itemName() {
        return itemName;
    }

    public int quantity() {
        return quantity;
    }

    /** What the buyer paid. */
    public long gross() {
        return gross;
    }

    /** The shop's cut. */
    public long commission() {
        return commission;
    }

    /** What the seller actually received. This is the number to show them. */
    public long net() {
        return net;
    }

    public String buyerName() {
        return buyerName;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putLong(TAG_SEQ, seq);
        tag.putLong(TAG_TIME, time);
        tag.putString(TAG_ITEM, itemName == null ? "" : itemName);
        tag.putInt(TAG_QUANTITY, quantity);
        tag.putLong(TAG_GROSS, gross);
        tag.putLong(TAG_COMMISSION, commission);
        tag.putLong(TAG_NET, net);
        tag.putString(TAG_BUYER, buyerName == null ? "" : buyerName);
        return tag;
    }

    @Nullable
    public static SaleNotice load(CompoundTag tag) {
        String item = tag.getString(TAG_ITEM);
        if (item.isBlank() || tag.getInt(TAG_QUANTITY) <= 0) {
            return null;
        }
        return new SaleNotice(tag.getLong(TAG_SEQ), tag.getLong(TAG_TIME), item,
                tag.getInt(TAG_QUANTITY), tag.getLong(TAG_GROSS), tag.getLong(TAG_COMMISSION),
                tag.getLong(TAG_NET), tag.getString(TAG_BUYER));
    }
}
