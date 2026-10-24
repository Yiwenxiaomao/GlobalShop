package com.example.globalshop.feature.teleport;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;

/**
 * Teleports: a random jump, and asking a named player to let you come to them.
 *
 * <h2>Consent is the whole feature</h2>
 * A teleport to a named player is a <em>request</em>. Nothing moves until the player being visited
 * agrees, either by clicking the button in the chat line or by opening their own teleport window and
 * pressing accept. Two players being able to appear next to each other on demand would make every
 * other protection pointless — a base, a chest room, a fight — so the answer is required and it can
 * be no.
 *
 * <h2>Finding somewhere safe to land</h2>
 * A random jump has to land on ground, not in lava, not in the sea, and not in the void. The search
 * picks a random column in range and asks the world for its surface height rather than scanning from
 * the sky down, then checks that the two blocks above it are clear and that neither they nor the
 * ground are fluid. Columns that fail are simply skipped, up to a bounded number of tries, so the
 * cost of a bad roll is capped rather than open-ended.
 *
 * <p>Requests are held in memory and nowhere else. A pending request is only meaningful while both
 * players are online — it expires in a minute — and surviving a restart would mean offering somebody
 * a teleport they asked for last week.
 */
public class TeleportService {

    /** How long a request stays open, unless the config says otherwise. */
    public static final int DEFAULT_REQUEST_SECONDS = 60;

    /** One outstanding request, from one player to another. */
    public static final class Request {

        private final UUID fromId;
        private final String fromName;
        private final UUID toId;
        private final long expiresAt;

        public Request(UUID fromId, String fromName, UUID toId, long expiresAt) {
            this.fromId = fromId;
            this.fromName = fromName;
            this.toId = toId;
            this.expiresAt = expiresAt;
        }

        public UUID fromId() {
            return fromId;
        }

        public String fromName() {
            return fromName;
        }

        public UUID toId() {
            return toId;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public boolean isExpired(long now) {
            return now >= expiresAt;
        }

        /** Whole seconds left, for a countdown in the window. */
        public long secondsLeft(long now) {
            return Math.max(0L, (expiresAt - now) / 1000L);
        }
    }

    private final MinecraftServer server;
    private final java.util.Random random = new java.util.Random();

    /** The request each player is waiting on — as the one who asked, or the one being asked. */
    private final Map<UUID, Request> outgoing = new HashMap<>();
    private final Map<UUID, Request> incoming = new HashMap<>();

    /** When each player may jump randomly again, in epoch milliseconds. */
    private final Map<UUID, Long> nextRandomAt = new HashMap<>();

    /** When each player may travel to a named player again. A separate clock from the random one. */
    private final Map<UUID, Long> nextTargetAt = new HashMap<>();

    public TeleportService(MinecraftServer server) {
        this.server = server;
    }

    /** How far a random jump may go. */
    public int maxDistance() {
        return Config.teleportMaxDistance();
    }

    /** The closest a random jump will land, so it is actually a journey. */
    public int minDistance() {
        return Math.min(100, maxDistance());
    }

    public long cooldownMillis() {
        return Config.teleportCooldownSeconds() * 1000L;
    }

    /** Seconds until this player may travel to a named player again, or 0. */
    public long targetCooldownLeft(UUID playerId) {
        long now = System.currentTimeMillis();
        Long at = nextTargetAt.get(playerId);
        if (at == null || at <= now) {
            nextTargetAt.remove(playerId);
            return 0L;
        }
        return (at - now) / 1000L;
    }

    /** What one teleport to a named player costs. */
    public long targetPrice() {
        return Config.teleportTargetPrice();
    }

    /** Seconds until this player may jump again, or 0. */
    public long randomCooldownLeft(UUID playerId) {
        long now = System.currentTimeMillis();
        // Expired entries are cleared here rather than swept: this is the only place they matter.
        Long at = nextRandomAt.get(playerId);
        if (at == null || at <= now) {
            nextRandomAt.remove(playerId);
            return 0L;
        }
        return (at - now) / 1000L;
    }

    @Nullable
    public Request incoming(UUID playerId) {
        Request request = incoming.get(playerId);
        if (request != null && request.isExpired(System.currentTimeMillis())) {
            forget(request);
            return null;
        }
        return request;
    }

    @Nullable
    public Request outgoing(UUID playerId) {
        Request request = outgoing.get(playerId);
        if (request != null && request.isExpired(System.currentTimeMillis())) {
            forget(request);
            return null;
        }
        return request;
    }

    private void forget(Request request) {
        outgoing.remove(request.fromId(), request);
        incoming.remove(request.toId(), request);
    }

    // ==================================================================
    // Random jump
    // ==================================================================

    /**
     * Jumps a player somewhere random in the same dimension.
     *
     * @return {@code SUCCESS}, or the reason nothing happened
     */
    public TeleportResult random(ServerPlayer player) {
        long left = randomCooldownLeft(player.getUUID());
        if (left > 0L) {
            return TeleportResult.ON_COOLDOWN;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return TeleportResult.WRONG_DIMENSION;
        }
        BlockPos landing = findLanding(level, player.blockPosition());
        if (landing == null) {
            return TeleportResult.NO_SAFE_SPOT;
        }
        player.teleportTo(level, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        nextRandomAt.put(player.getUUID(), System.currentTimeMillis() + cooldownMillis());
        return TeleportResult.SUCCESS;
    }

    /**
     * Looks for a column to land in.
     *
     * <p>Bounded to a fixed number of attempts: a player standing in the middle of an ocean would
     * otherwise make the server scan forever looking for dry land, and the honest answer is that
     * there is nowhere to land rather than a stall.
     */
    @Nullable
    private BlockPos findLanding(ServerLevel level, BlockPos from) {
        int min = minDistance();
        int max = Math.max(min, maxDistance());
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = min + random.nextInt(Math.max(1, max - min));
            int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
            // Reading the heightmap loads the chunk, which is what makes the check below meaningful.
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafe(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /** Whether a player can stand here: solid floor, two clear blocks, and nothing wet or burning. */
    private boolean isSafe(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() + 1
                || pos.getY() >= level.getMaxBuildHeight() - 2) {
            // The void and the build ceiling are not somewhere to drop a player.
            return false;
        }
        BlockState ground = level.getBlockState(pos.below());
        if (ground.isAir() || ground.getCollisionShape(level, pos.below()).isEmpty()) {
            return false;
        }
        if (ground.is(Blocks.LAVA) || ground.is(Blocks.MAGMA_BLOCK)
                || ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.FIRE)
                || ground.is(Blocks.SOUL_FIRE) || ground.is(Blocks.CACTUS)
                || ground.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        if (!level.getFluidState(pos.below()).isEmpty()
                || level.getFluidState(pos).is(FluidTags.WATER)
                || level.getFluidState(pos).is(FluidTags.LAVA)) {
            return false;
        }
        // Two blocks of headroom, so a player does not arrive suffocating inside a block.
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    // ==================================================================
    // Asking to visit somebody
    // ==================================================================

    /** Starts, or replaces, a request to visit a player by name. */
    public TeleportResult request(ServerPlayer from, String targetName) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return TeleportResult.NO_SUCH_PLAYER;
        }
        return request(from, target);
    }

    /**
     * Starts, or replaces, a request to visit a specific player.
     *
     * <p>The split exists because resolving a name is the caller's business: a name arrives from the
     * network, and looking it up belongs where the network is read. It also means the rules below can
     * be exercised without a player having to be online.
     */
    public TeleportResult request(ServerPlayer from, ServerPlayer target) {
        long cooling = targetCooldownLeft(from.getUUID());
        if (cooling > 0L) {
            // Checked here rather than when the teleport happens, so a player is told before they
            // ask somebody to stop what they are doing.
            return TeleportResult.ON_COOLDOWN;
        }
        if (target.getUUID().equals(from.getUUID())) {
            return TeleportResult.THAT_IS_YOU;
        }
        if (!target.level().dimension().equals(from.level().dimension())) {
            // Cross-dimension teleports are a different feature, and one worth thinking about twice.
            return TeleportResult.WRONG_DIMENSION;
        }
        long now = System.currentTimeMillis();
        Request previous = outgoing(from.getUUID());
        if (previous != null) {
            // One outstanding request per player: asking somebody else replaces the last one, so a
            // player cannot line up five people and wait to see who accepts first.
            forget(previous);
        }
        Request request = new Request(from.getUUID(), from.getGameProfile().getName(),
                target.getUUID(), now + Config.teleportRequestSeconds() * 1000L);
        outgoing.put(from.getUUID(), request);
        incoming.put(target.getUUID(), request);
        return TeleportResult.SUCCESS;
    }

    /** Accepts the request this player is being asked about, and moves the asker to them. */
    public TeleportResult accept(ServerPlayer target) {
        Request request = incoming(target.getUUID());
        if (request == null) {
            return TeleportResult.NO_REQUEST;
        }
        ServerPlayer from = server.getPlayerList().getPlayer(request.fromId());
        if (from == null) {
            // They logged out while waiting; there is nobody left to move. The request is consumed
            // either way, because there is no longer anybody who could answer it.
            forget(request);
            return TeleportResult.NO_SUCH_PLAYER;
        }
        if (!from.level().dimension().equals(target.level().dimension())) {
            forget(request);
            return TeleportResult.WRONG_DIMENSION;
        }
        // Paid for by the player who travels, at the moment they travel. A request that is refused
        // is therefore free, and a refusal for want of money leaves the request open so that topping
        // up and accepting again still works.
        long price = targetPrice();
        EconomyService economy = EconomyServices.get(server);
        economy.ensureAccount(from, Config.initialBalance());
        if (!economy.hasEnough(from.getUUID(), price)) {
            return TeleportResult.INSUFFICIENT_FUNDS;
        }
        if (!economy.removeBalance(from.getUUID(), price).isSuccess()) {
            return TeleportResult.INSUFFICIENT_FUNDS;
        }
        from.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot());
        // Charged, and only now does the trip start its cooldown.
        nextTargetAt.put(from.getUUID(), System.currentTimeMillis()
                + Config.teleportTargetCooldownSeconds() * 1000L);
        forget(request);
        // The payer's balance moved; a window showing the old figure would look like a free ride.
        com.example.globalshop.internal.economy.net.BalanceUpdatePacket.push(from);
        return TeleportResult.SUCCESS;
    }

    /** Refuses the request this player is being asked about. */
    public TeleportResult deny(ServerPlayer target) {
        Request request = incoming(target.getUUID());
        if (request == null) {
            return TeleportResult.NO_REQUEST;
        }
        forget(request);
        return TeleportResult.SUCCESS;
    }

    /** Cancels a request this player made. */
    public TeleportResult cancel(ServerPlayer from) {
        Request request = outgoing(from.getUUID());
        if (request == null) {
            return TeleportResult.NO_REQUEST;
        }
        forget(request);
        return TeleportResult.SUCCESS;
    }

    /** The name shown for whatever this player is waiting on, or {@code null}. */
    @Nullable
    public Component waitingWith(UUID playerId) {
        Request in = incoming(playerId);
        if (in != null) {
            return Component.literal(in.fromName());
        }
        Request out = outgoing(playerId);
        if (out != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(out.toId());
            return Component.literal(target == null ? "?" : target.getGameProfile().getName());
        }
        return null;
    }

    /** Drops everything for a player who left, so nobody is left waiting on them. */
    public void forgetPlayer(UUID playerId) {
        Request in = incoming.get(playerId);
        if (in != null) {
            forget(in);
        }
        Request out = outgoing.get(playerId);
        if (out != null) {
            forget(out);
        }
        nextRandomAt.remove(playerId);
        nextTargetAt.remove(playerId);
    }

    /** Only for tests and for the server-stopping hook. */
    public void clear() {
        outgoing.clear();
        incoming.clear();
        nextRandomAt.clear();
        nextTargetAt.clear();
    }
}
