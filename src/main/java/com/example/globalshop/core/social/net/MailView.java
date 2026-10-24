package com.example.globalshop.core.social.net;

import com.example.globalshop.core.social.Mail;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One mail as it travels to a client.
 *
 * <p>A flat snapshot, like every other view in this mod: the client cannot reach server state
 * through it, and it carries only what the screen draws. {@code expiresAt} is decided by the server
 * rather than worked out on the client, so the wording "expires in 3 days" cannot disagree with
 * whether the mail is actually still claimable.
 *
 * @param display    the first stack, for the icon and the name
 * @param totalCount every item in the delivery, which may be several stacks
 * @param stackCount how many stacks, so the screen can say "3 items (2 stacks)"
 */
public record MailView(long id,
                       ItemStack display,
                       int totalCount,
                       int stackCount,
                       long createdAt,
                       long expiresAt,
                       String sourceKey) {

    public static MailView of(Mail mail) {
        List<ItemStack> items = mail.items();
        return new MailView(
                mail.id(),
                items.isEmpty() ? ItemStack.EMPTY : items.get(0).copy(),
                mail.totalCount(),
                items.size(),
                mail.createdAt(),
                mail.expiresAt(),
                mail.source().translationKey());
    }

    /** Whether this mail can still be claimed, given the server's clock. */
    public boolean expiredAt(long now) {
        return now >= expiresAt;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(id);
        buf.writeItem(display);
        buf.writeVarInt(Math.max(0, totalCount));
        buf.writeVarInt(Math.max(0, stackCount));
        buf.writeLong(createdAt);
        buf.writeLong(expiresAt);
        buf.writeUtf(sourceKey == null ? "" : sourceKey, 64);
    }

    public static MailView decode(FriendlyByteBuf buf) {
        return new MailView(buf.readVarLong(), buf.readItem(), buf.readVarInt(), buf.readVarInt(),
                buf.readLong(), buf.readLong(), buf.readUtf(64));
    }

    @Nullable
    public static MailView decodeOrNull(FriendlyByteBuf buf) {
        return buf.readBoolean() ? decode(buf) : null;
    }
}
