package com.example.globalshop.shop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side persistence for the shared market.
 *
 * <p>Holds every listing (and therefore every escrowed item), the trade log, and the
 * mailbox of items waiting for offline players. Like the economy this is a
 * {@link SavedData} in the world save -- for a dedicated server,
 * {@code world/data/globalshop_shop.dat} -- so it is shared by the whole server,
 * survives restarts, and is included in world backups. It is never stored client
 * side and the client has no code path that can write to it.
 *
 * <p>Access is expected while holding {@code synchronized (data)}; see
 * {@link ShopService}.
 */
public class ShopSavedData extends SavedData {

    public static final String DATA_NAME = "globalshop_shop";

    private static final String TAG_LISTINGS = "Listings";
    private static final String TAG_LOG = "Log";
    private static final String TAG_MAILBOX = "Mailbox";
    private static final String TAG_MAILS = "Mails";
    private static final String TAG_NEXT_MAIL = "NextMailId";
    private static final String TAG_NEXT_SEQ = "NextLogSeq";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_NOTICES = "Notices";
    private static final String TAG_NOTICE = "Notice";
    private static final String TAG_VIP = "Vip";
    private static final String TAG_VIP_UNTIL = "VipUntil";
    private static final String TAG_VIP_AUTO = "VipAutoRenew";
    private static final String TAG_UNTIL = "Until";
    private static final String TAG_BLACKLIST = "Blacklist";

    /** listing id -> listing. Insertion ordered so iteration is deterministic. */
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();

    /** Oldest first. Trimmed from the front once it exceeds the configured retention. */
    private final Deque<TradeLogEntry> log = new ArrayDeque<>();

    /** player uuid -> deliveries waiting for them, oldest first. */
    private final Map<UUID, List<Mail>> mailbox = new HashMap<>();

    private long nextMailId = 1;

    /**
     * Sale notices waiting for a seller who was offline when their lot sold.
     *
     * <p>Stored beside the mailbox and for the same reason: the requirement is that a seller
     * finds out what sold and what they were paid the next time they log in, and the only place
     * that survives a restart is the world save.
     */
    private final Map<UUID, List<SaleNotice>> notices = new HashMap<>();

    /**
     * When each player's VIP runs out, in epoch milliseconds.
     *
     * <p>A time rather than a flag, because VIP is bought by the period and not forever. A flag
     * would have to be paired with a second record of when it lapses, and the two could disagree.
     */
    private final Map<UUID, Long> vipUntil = new HashMap<>();

    /** Players who asked to be charged again when their period runs out. */
    private final Set<UUID> vipAutoRenew = new HashSet<>();

    /**
     * Items an operator has forbidden from being listed, by registry name.
     *
     * <p>Registry names rather than whole stacks: the operator's intent is "nobody may sell
     * diamonds", and blocking one particular renamed diamond would invite exactly the workaround
     * the rule exists to stop.
     */
    private final Set<String> blacklist = new HashSet<>();

    private long nextLogSeq = 1L;

    public ShopSavedData() {
    }

    // ------------------------------------------------------------------
    // SavedData plumbing
    // ------------------------------------------------------------------

    public static ShopSavedData load(CompoundTag tag) {
        ShopSavedData data = new ShopSavedData();

        ListTag listingTags = tag.getList(TAG_LISTINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < listingTags.size(); i++) {
            Listing listing = Listing.load(listingTags.getCompound(i));
            if (listing != null) {
                data.listings.put(listing.id(), listing);
            }
        }

        ListTag logTags = tag.getList(TAG_LOG, Tag.TAG_COMPOUND);
        for (int i = 0; i < logTags.size(); i++) {
            TradeLogEntry entry = TradeLogEntry.load(logTags.getCompound(i));
            if (entry != null) {
                data.log.addLast(entry);
            }
        }

        ListTag mailboxTags = tag.getList(TAG_MAILBOX, Tag.TAG_COMPOUND);
        for (int i = 0; i < mailboxTags.size(); i++) {
            CompoundTag ownerTag = mailboxTags.getCompound(i);
            if (!ownerTag.hasUUID(TAG_OWNER)) {
                continue;
            }
            UUID owner = ownerTag.getUUID(TAG_OWNER);
            List<Mail> mails = new ArrayList<>();
            ListTag mailTags = ownerTag.getList(TAG_MAILS, Tag.TAG_COMPOUND);
            for (int j = 0; j < mailTags.size(); j++) {
                Mail mail = Mail.load(mailTags.getCompound(j));
                if (mail != null) {
                    mails.add(mail);
                }
            }
            if (mails.isEmpty()) {
                // The format before mail existed: a bare list of stacks with no time on them.
                // Read them as mail created now rather than discarding a player's goods during an
                // upgrade; a fresh lifetime is the generous reading and the safe one.
                ListTag itemTags = ownerTag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
                List<ItemStack> legacy = new ArrayList<>(itemTags.size());
                for (int j = 0; j < itemTags.size(); j++) {
                    ItemStack stack = ItemStack.of(itemTags.getCompound(j));
                    if (!stack.isEmpty()) {
                        legacy.add(stack);
                    }
                }
                if (!legacy.isEmpty()) {
                    mails.add(new Mail(data.nextMailId++, System.currentTimeMillis(),
                            Mail.Source.MARKET, legacy));
                }
            }
            if (!mails.isEmpty()) {
                data.mailbox.put(owner, mails);
            }
        }
        data.nextMailId = Math.max(data.nextMailId, tag.getLong(TAG_NEXT_MAIL));

        data.nextLogSeq = Math.max(1L, tag.getLong(TAG_NEXT_SEQ));

        ListTag noticeTags = tag.getList(TAG_NOTICES, Tag.TAG_COMPOUND);
        for (int i = 0; i < noticeTags.size(); i++) {
            CompoundTag ownerTag = noticeTags.getCompound(i);
            if (!ownerTag.hasUUID(TAG_OWNER)) {
                continue;
            }
            UUID owner = ownerTag.getUUID(TAG_OWNER);
            ListTag lines = ownerTag.getList(TAG_NOTICE, Tag.TAG_COMPOUND);
            List<SaleNotice> pending = new ArrayList<>(lines.size());
            for (int j = 0; j < lines.size(); j++) {
                SaleNotice notice = SaleNotice.load(lines.getCompound(j));
                if (notice != null) {
                    pending.add(notice);
                }
            }
            if (!pending.isEmpty()) {
                data.notices.put(owner, pending);
            }
        }

        ListTag blacklistTags = tag.getList(TAG_BLACKLIST, Tag.TAG_STRING);
        for (int i = 0; i < blacklistTags.size(); i++) {
            String blocked = blacklistTags.getString(i);
            if (!blocked.isBlank()) {
                data.blacklist.add(blocked);
            }
        }

        ListTag vipTags = tag.getList(TAG_VIP_UNTIL, Tag.TAG_COMPOUND);
        for (int i = 0; i < vipTags.size(); i++) {
            CompoundTag ownerTag = vipTags.getCompound(i);
            if (ownerTag.hasUUID(TAG_OWNER)) {
                data.vipUntil.put(ownerTag.getUUID(TAG_OWNER), ownerTag.getLong(TAG_UNTIL));
            }
        }
        // The format before VIP expired: a bare list of ids that meant "VIP, forever". Read as one
        // period from now, which is the generous reading of a promise the old version made.
        ListTag legacyVip = tag.getList(TAG_VIP, Tag.TAG_COMPOUND);
        long from = System.currentTimeMillis();
        for (int i = 0; i < legacyVip.size(); i++) {
            CompoundTag ownerTag = legacyVip.getCompound(i);
            if (ownerTag.hasUUID(TAG_OWNER)) {
                data.vipUntil.merge(ownerTag.getUUID(TAG_OWNER),
                        from + com.example.globalshop.Config.vipMillis(), Math::max);
            }
        }

        ListTag autoTags = tag.getList(TAG_VIP_AUTO, Tag.TAG_COMPOUND);
        for (int i = 0; i < autoTags.size(); i++) {
            CompoundTag ownerTag = autoTags.getCompound(i);
            if (ownerTag.hasUUID(TAG_OWNER)) {
                data.vipAutoRenew.add(ownerTag.getUUID(TAG_OWNER));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag listingTags = new ListTag();
        for (Listing listing : listings.values()) {
            listingTags.add(listing.save(new CompoundTag()));
        }
        tag.put(TAG_LISTINGS, listingTags);

        ListTag logTags = new ListTag();
        for (TradeLogEntry entry : log) {
            logTags.add(entry.save(new CompoundTag()));
        }
        tag.put(TAG_LOG, logTags);

        ListTag mailboxTags = new ListTag();
        List<UUID> owners = new ArrayList<>(mailbox.keySet());
        owners.sort(Comparator.comparing(UUID::toString));
        for (UUID owner : owners) {
            List<Mail> mails = mailbox.get(owner);
            if (mails == null || mails.isEmpty()) {
                continue;
            }
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(TAG_OWNER, owner);
            ListTag mailTags = new ListTag();
            for (Mail mail : mails) {
                mailTags.add(mail.save());
            }
            ownerTag.put(TAG_MAILS, mailTags);
            mailboxTags.add(ownerTag);
        }
        tag.put(TAG_MAILBOX, mailboxTags);
        tag.putLong(TAG_NEXT_MAIL, nextMailId);

        tag.putLong(TAG_NEXT_SEQ, nextLogSeq);

        ListTag noticeTags = new ListTag();
        List<UUID> noticeOwners = new ArrayList<>(notices.keySet());
        noticeOwners.sort(Comparator.comparing(UUID::toString));
        for (UUID owner : noticeOwners) {
            List<SaleNotice> pending = notices.get(owner);
            if (pending == null || pending.isEmpty()) {
                continue;
            }
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(TAG_OWNER, owner);
            ListTag lines = new ListTag();
            for (SaleNotice notice : pending) {
                lines.add(notice.save(new CompoundTag()));
            }
            ownerTag.put(TAG_NOTICE, lines);
            noticeTags.add(ownerTag);
        }
        tag.put(TAG_NOTICES, noticeTags);

        ListTag vipTags = new ListTag();
        List<UUID> members = new ArrayList<>(vipUntil.keySet());
        members.sort(Comparator.comparing(UUID::toString));
        for (UUID member : members) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(TAG_OWNER, member);
            ownerTag.putLong(TAG_UNTIL, vipUntil.getOrDefault(member, 0L));
            vipTags.add(ownerTag);
        }
        tag.put(TAG_VIP_UNTIL, vipTags);

        ListTag autoTags = new ListTag();
        List<UUID> autoMembers = new ArrayList<>(vipAutoRenew);
        autoMembers.sort(Comparator.comparing(UUID::toString));
        for (UUID member : autoMembers) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(TAG_OWNER, member);
            autoTags.add(ownerTag);
        }
        tag.put(TAG_VIP_AUTO, autoTags);

        ListTag blacklistTags = new ListTag();
        for (String blocked : blacklist()) {
            blacklistTags.add(net.minecraft.nbt.StringTag.valueOf(blocked));
        }
        tag.put(TAG_BLACKLIST, blacklistTags);
        return tag;
    }

    /** Always resolves through the overworld so the whole server shares one market. */
    public static ShopSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(ShopSavedData::load, ShopSavedData::new, DATA_NAME);
    }

    // ------------------------------------------------------------------
    // Listings
    //
    // The read accessors are public so diagnostics and the self test can
    // inspect the market; the mutators stay package private so only
    // ShopService can change it, and only while holding the monitor.
    // ------------------------------------------------------------------

    /** Look up a listing by id. Read-only; use {@link ShopService} to change anything. */
    public Listing listing(UUID id) {
        return listings.get(id);
    }

    Collection<Listing> allListings() {
        return listings.values();
    }

    void putListing(Listing listing) {
        listings.put(listing.id(), listing);
    }

    Listing removeListing(UUID id) {
        return listings.remove(id);
    }

    /** Total listings on file, sold-out ones included. */
    public int listingCount() {
        return listings.size();
    }

    int listingCountOf(UUID sellerId) {
        int count = 0;
        for (Listing listing : listings.values()) {
            if (listing.isSeller(sellerId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Serialises and re-reads this data, exactly as a server restart does.
     *
     * <p>Used by the self test to prove that listings and escrowed items survive a
     * save/load cycle.
     */
    public static ShopSavedData reload(ShopSavedData existing) {
        return load(existing.save(new CompoundTag()));
    }

    // ------------------------------------------------------------------
    // Trade log
    // ------------------------------------------------------------------

    long nextSeq() {
        return nextLogSeq++;
    }

    void appendLog(TradeLogEntry entry, int retention) {
        log.addLast(entry);
        if (retention > 0) {
            while (log.size() > retention) {
                log.removeFirst();
            }
        }
    }

    /** @return up to {@code limit} entries, newest first. */
    List<TradeLogEntry> logTail(int limit) {
        List<TradeLogEntry> newestFirst = new ArrayList<>(log);
        java.util.Collections.reverse(newestFirst);
        if (limit > 0 && newestFirst.size() > limit) {
            return newestFirst.subList(0, limit);
        }
        return newestFirst;
    }

    int logSize() {
        return log.size();
    }

    /** Number of trade log entries on file. */
    public int logEntryCount() {
        return log.size();
    }

    /** Trims the log to the configured retention. Used when the config is reloaded. */
    void trimLog(int retention) {
        if (retention <= 0) {
            return;
        }
        while (log.size() > retention) {
            log.removeFirst();
        }
    }

    // ------------------------------------------------------------------
    // Mailbox: deliveries that could not be handed over
    // ------------------------------------------------------------------

    /** Files a delivery, making room from lapsed mail if it has to.
     *
     * @return whether it was accepted; {@code false} means the mailbox is full of live mail
     */
    boolean addMail(UUID owner, Mail mail, long now) {
        List<Mail> mails = mailbox.computeIfAbsent(owner, key -> new ArrayList<>());
        if (mails.size() >= com.example.globalshop.Config.mailboxLimit()) {
            // Lapsed mail holds nothing the player could still claim, so discarding it to make
            // room costs them nothing -- and it means a mailbox full of old mail clears itself
            // instead of locking the player out of receiving anything ever again.
            mails.removeIf(existing -> existing.isExpired(now));
            if (mails.isEmpty()) {
                mailbox.remove(owner);
                mails = mailbox.computeIfAbsent(owner, key -> new ArrayList<>());
            }
        }
        if (mails.size() >= com.example.globalshop.Config.mailboxLimit()) {
            return false;
        }
        mails.add(mail);
        return true;
    }

    /**
     * Whether a new mail would be refused right now.
     *
     * <p>Lapsed mail does not block, because {@link #addMail} discards it to make room — so a
     * player whose mailbox is full of old mail is not locked out.
     */
    boolean mailSpaceFull(UUID owner, long now) {
        List<Mail> mails = mailbox.get(owner);
        if (mails == null || mails.size() < com.example.globalshop.Config.mailboxLimit()) {
            return false;
        }
        return mails.stream().noneMatch(mail -> mail.isExpired(now));
    }

    /** Live list for one owner. The caller holds the lock and must not keep the list. */
    public List<Mail> mailsOf(UUID owner) {
        List<Mail> mails = mailbox.get(owner);
        return mails == null ? List.of() : mails;
    }

    public int mailCount(UUID owner) {
        List<Mail> mails = mailbox.get(owner);
        return mails == null ? 0 : mails.size();
    }

    boolean hasMail(UUID owner) {
        return mailCount(owner) > 0;
    }

    long nextMailId() {
        return nextMailId++;
    }

    /** @return the removed mail, or {@code null} if this player has no mail with that id */
    Mail removeMail(UUID owner, long mailId) {
        List<Mail> mails = mailbox.get(owner);
        if (mails == null) {
            return null;
        }
        for (int i = 0; i < mails.size(); i++) {
            if (mails.get(i).id() == mailId) {
                Mail removed = mails.remove(i);
                if (mails.isEmpty()) {
                    mailbox.remove(owner);
                }
                return removed;
            }
        }
        return null;
    }

    /** Puts a mail back after only part of it could be delivered. */
    void restoreMail(UUID owner, Mail mail) {
        mailbox.computeIfAbsent(owner, key -> new ArrayList<>()).add(mail);
    }

    /** Number of players with mail waiting. Diagnostics. */
    public int mailHolders() {
        return mailbox.size();
    }

    // ------------------------------------------------------------------
    // Sale notices
    // ------------------------------------------------------------------

    /** Queues one notice for a seller who was not there to be told. */
    void addNotice(UUID owner, SaleNotice notice) {
        notices.computeIfAbsent(owner, key -> new ArrayList<>()).add(notice);
    }

    boolean hasNotices(UUID owner) {
        List<SaleNotice> pending = notices.get(owner);
        return pending != null && !pending.isEmpty();
    }

    /** Removes and returns every notice waiting for this player. */
    List<SaleNotice> takeNotices(UUID owner) {
        List<SaleNotice> pending = notices.remove(owner);
        return pending == null ? List.of() : pending;
    }

    /** How many players are waiting to be told something. Diagnostics. */
    public int noticeHolders() {
        return notices.size();
    }

    // ------------------------------------------------------------------
    // VIP
    // ------------------------------------------------------------------

    public boolean isVip(UUID playerId, long now) {
        Long until = vipUntil.get(playerId);
        return until != null && until > now;
    }

    /** When this player's VIP lapses. {@code 0} means they have never had any. */
    long vipUntil(UUID playerId) {
        return vipUntil.getOrDefault(playerId, 0L);
    }

    /** Extends VIP to at least this moment. Never shortens: buying while still a member adds time. */
    void grantVip(UUID playerId, long untilMillis) {
        vipUntil.merge(playerId, untilMillis, Math::max);
    }

    void revokeVip(UUID playerId) {
        vipUntil.remove(playerId);
        vipAutoRenew.remove(playerId);
    }

    boolean vipAutoRenew(UUID playerId) {
        return vipAutoRenew.contains(playerId);
    }

    void setVipAutoRenew(UUID playerId, boolean on) {
        if (on) {
            vipAutoRenew.add(playerId);
        } else {
            vipAutoRenew.remove(playerId);
        }
    }

    /** Everyone who asked to be charged again when their period ends. */
    List<UUID> autoRenewers() {
        return new ArrayList<>(vipAutoRenew);
    }

    /** Members whose period is still running. Diagnostics. */
    public int vipCount(long now) {
        int count = 0;
        for (Long until : vipUntil.values()) {
            if (until != null && until > now) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // The item blacklist
    // ------------------------------------------------------------------

    public boolean isBlacklisted(String itemId) {
        return blacklist.contains(itemId);
    }

    /** @return whether the entry was not already there */
    boolean addToBlacklist(String itemId) {
        return blacklist.add(itemId);
    }

    /** @return whether an entry was actually removed */
    boolean removeFromBlacklist(String itemId) {
        return blacklist.remove(itemId);
    }

    /** Every blocked item, in a stable order so the screen does not reshuffle on each open. */
    public List<String> blacklist() {
        List<String> sorted = new ArrayList<>(blacklist);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    public int blacklistSize() {
        return blacklist.size();
    }
}
