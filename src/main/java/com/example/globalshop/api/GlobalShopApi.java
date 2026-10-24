package com.example.globalshop.api;

import com.example.globalshop.api.feature.FeaturePlaces;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;

/**
 * The one class an addon needs to name.
 *
 * <p>Everything supported is reachable from here, so that the shape of the mod's internals is not
 * something an addon has to learn. See the package documentation for what is promised and what is
 * not.
 *
 * <p>Every method here is safe to call from mod setup. The economy is created on first request for a
 * given server, and the place registry exists before any server does.
 *
 * @since 1.1.0
 */
public final class GlobalShopApi {

    /** The mod id, for resource locations and dependency declarations. */
    public static final String MOD_ID = "globalshop";

    /**
     * The API version, which changes when something in this package changes incompatibly.
     *
     * <p>Separate from the mod version on purpose: a release that only fixes a feature should not
     * make addons think they have to be rebuilt. Compare this, not the mod version, when deciding
     * whether an addon is compatible.
     */
    public static final int API_VERSION = 1;

    private GlobalShopApi() {
    }

    /**
     * The economy for a server — the same instance the mod's own shop uses.
     *
     * <p>Never a copy and never a wrapper: an addon that charges a player and the market that charges
     * the same player are working on the same balances.
     *
     * <p>The server may be {@code null} when no server is running, in which case there is nothing to
     * do any economy work against and this returns {@code null} rather than inventing an instance
     * that would be thrown away.
     */
    public static EconomyService economy(MinecraftServer server) {
        return server == null ? null : EconomyServices.get(server);
    }

    /** The registry for page two's places. Never null. */
    public static FeaturePlaces places() {
        return FeaturePlaces.get();
    }

    /** @return whether GlobalShop is loaded, for an addon that wants to say so in its log. */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().getModContainerById(MOD_ID).isPresent();
    }

    /**
     * The running mod version, or {@code "unknown"} outside a modded environment.
     *
     * <p>For logging and for a diagnostic command. Compatibility decisions belong on
     * {@link #API_VERSION}.
     */
    public static String modVersion() {
        if (ModList.get() == null) {
            return "unknown";
        }
        return ModList.get().getModContainerById(MOD_ID)
                .map(ModContainer::getModInfo)
                .map(info -> info.getVersion().toString())
                .orElse("unknown");
    }

    /**
     * The name an addon should use for its own translation keys and resource locations.
     *
     * <p>A convenience that also documents the convention: an addon's keys are its own business, and
     * nothing in GlobalShop's namespace should be written to by anybody else.
     */
    public static String key(String addonId, String path) {
        return Objects.requireNonNull(addonId, "addonId") + "." + Objects.requireNonNull(path, "path");
    }
}
