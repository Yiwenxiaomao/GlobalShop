package com.example.globalshop.registry;

import com.example.globalshop.GlobalShop;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own creative tab, so both terminals are easy to find.
 *
 * <p>Every item the mod adds must be listed here. Forgetting one does not fail to compile and
 * does not fail the self test — it simply means the item exists, can be crafted by nothing and
 * traded by nothing, and is invisible to anyone looking for it. That is exactly what happened
 * to the server shop terminal: the block and its item were registered, but this list was not
 * updated, so the tab showed one terminal instead of two.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GlobalShop.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.globalshop"))
                    .icon(() -> new ItemStack(ModItems.SHOP_TERMINAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SHOP_TERMINAL.get());
                        output.accept(ModItems.SERVER_SHOP_TERMINAL.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
