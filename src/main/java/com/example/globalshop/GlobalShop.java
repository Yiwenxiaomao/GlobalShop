package com.example.globalshop;

import com.example.globalshop.client.ClientSetup;
import com.example.globalshop.command.ModArgumentTypes;
import com.example.globalshop.event.ShopEvents;
import com.example.globalshop.menu.ServerShopMenu;
import com.example.globalshop.menu.ShopMenu;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.registry.ModBlocks;
import com.example.globalshop.registry.ModCreativeTabs;
import com.example.globalshop.registry.ModItems;
import com.example.globalshop.registry.ModMenus;
import com.example.globalshop.servershop.ServerShopService;
import com.example.globalshop.servershop.ServerShopServices;
import com.example.globalshop.shop.ShopService;
import com.example.globalshop.shop.ShopServices;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * GlobalShop: one shared marketplace for every player on the server, with a
 * built-in economy.
 *
 * <h2>Where the truth lives</h2>
 * Every authoritative value -- balances, listings, escrowed stock, ownership, the
 * trade log -- lives in server-side {@code SavedData} inside the world save
 * ({@code world/data/globalshop_economy.dat} and {@code world/data/globalshop_shop.dat}).
 * The client holds only a cache it was sent, which it re-fetches on every open and
 * on every reconnect. Nothing a client sends is trusted: requests carry at most
 * "which listing" and "how many", and the server decides everything else.
 *
 * <p>Run on the dedicated server as well as the client. In single player or on a LAN
 * world the integrated server is the authority, so data still lands in that world's
 * {@code data} directory rather than anywhere global.
 */
@Mod(GlobalShop.MODID)
public class GlobalShop {

    public static final String MODID = "globalshop";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Forge marks {@code FMLJavaModLoadingContext.get()} and
     * {@code ModLoadingContext.get()} as deprecated-for-removal ahead of 1.20.2, but
     * they are the pattern the 1.20.1 MDK itself uses and they remain present
     * throughout Forge 47.x (removing them inside a minor branch would break every
     * existing 1.20.1 mod). They are suppressed here rather than replaced with a
     * constructor signature this Forge build may not inject.
     */
    @SuppressWarnings("removal")
    public GlobalShop() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Registry order matters: blocks are registered before the BlockItem that wraps them.
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onConfigReload);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Gameplay events (commands, login, logout, server lifecycle).
        MinecraftForge.EVENT_BUS.register(ShopEvents.class);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Isolated so the dedicated server never loads client-only classes.
            modBus.addListener(ClientSetup::onClientSetup);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Custom argument types must be known to both sides before any command tree
            // is serialised, or every login is refused. See ModArgumentTypes.
            ModArgumentTypes.register();
            // Networking must be registered after both sides exist, on the mod bus.
            ModNetwork.register();
        });
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        Config.onLoad();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        Config.onLoad();
    }

    // ------------------------------------------------------------------
    // Helpers shared by the block, the item and the commands
    // ------------------------------------------------------------------

    /** Server-side shop operations for a server. */
    public static ShopService shop(MinecraftServer server) {
        return ShopServices.get(server);
    }

    public static ShopService shop(ServerPlayer player) {
        return ShopServices.get(player);
    }

    /**
     * Opens the marketplace for a player.
     *
     * <p>Always called on the server. The client does not open its own screen; it
     * receives the open-screen packet and then asks the server for the listing page,
     * so a client cannot show itself a shop the server did not agree to open.
     */
    public static void openShop(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, ignored) -> new ShopMenu(windowId, inventory),
                Component.translatable("container.globalshop.shop")));
    }

    /** Server-side server-shop operations for a server. */
    public static ServerShopService serverShop(MinecraftServer server) {
        return ServerShopServices.get(server);
    }

    public static ServerShopService serverShop(ServerPlayer player) {
        return ServerShopServices.get(player);
    }

    /**
     * Opens the official server shop for a player.
     *
     * <p>A different menu type and a different screen from {@link #openShop}: the two shops are
     * separate features, and nothing about opening one should be able to open the other.
     */
    public static void openServerShop(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, ignored) -> new ServerShopMenu(windowId, inventory),
                Component.translatable("container.globalshop.server_shop")));
    }
}
