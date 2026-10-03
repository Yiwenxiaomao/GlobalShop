package com.example.globalshop.shop;

import java.util.Comparator;
import net.minecraft.network.chat.Component;

/**
 * Orderings offered by the market GUI.
 *
 * <p>Every comparator ends with {@code listedAt} (and then the listing id) as a
 * tie-breaker so that paging through results is stable: the same listing cannot
 * appear twice or vanish between pages because two entries compared equal.
 */
public enum ShopSort {

    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    TIME_NEWEST("time_newest"),
    TIME_OLDEST("time_oldest"),
    SELLER("seller"),
    NAME("name");

    public static final ShopSort DEFAULT = TIME_NEWEST;

    private final String id;

    ShopSort(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "globalshop.sort." + id;
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    /** Next ordering when the player cycles the sort button. */
    public ShopSort next() {
        ShopSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ShopSort byId(String id) {
        for (ShopSort sort : values()) {
            if (sort.id.equals(id)) {
                return sort;
            }
        }
        return DEFAULT;
    }

    public Comparator<Listing> comparator() {
        Comparator<Listing> byTimeNewestFirst = Comparator.comparingLong(Listing::listedAt).reversed();
        Comparator<Listing> stable = byTimeNewestFirst.thenComparing(l -> l.id().toString());
        switch (this) {
            case PRICE_ASC:
                return Comparator.comparingLong(Listing::unitPrice).thenComparing(stable);
            case PRICE_DESC:
                return Comparator.comparingLong(Listing::unitPrice).reversed().thenComparing(stable);
            case TIME_OLDEST:
                return Comparator.comparingLong(Listing::listedAt)
                        .thenComparing(l -> l.id().toString());
            case SELLER:
                return Comparator.comparing((Listing l) -> l.sellerName().toLowerCase(java.util.Locale.ROOT))
                        .thenComparing(stable);
            case NAME:
                return Comparator.comparing((Listing l) -> l.template().getHoverName().getString())
                        .thenComparing(stable);
            case TIME_NEWEST:
            default:
                return stable;
        }
    }
}
