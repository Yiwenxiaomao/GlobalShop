package com.example.globalshop.core.command;

import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;

/**
 * Registration of the mod's custom Brigadier argument types.
 *
 * <h2>Why this class has to exist</h2>
 * When a player joins, the server serialises its entire command tree and sends it to
 * the client ({@code PlayerList.placeNewPlayer} calls {@code Commands.sendCommands}).
 * Each argument node is written by looking its argument type up in
 * {@link ArgumentTypeInfos}'s class table, and a type that is not in that table makes
 * the lookup throw:
 *
 * <pre>
 * IllegalArgumentException: Unrecognized argument type ...MoneyArgumentType
 * </pre>
 *
 * <p>That exception happens while the player is being placed in the world, so the
 * player is disconnected with "Invalid player data" and never gets in. The failure is
 * on the <em>server</em> side and would happen with a completely vanilla client too.
 *
 * <p>Registration must therefore happen on <b>both</b> sides -- the server to write the
 * node, the client to read it back -- which is why this runs from
 * {@code FMLCommonSetupEvent} rather than from any client- or server-only hook.
 */
public final class ModArgumentTypes {

    private static boolean registered;

    private ModArgumentTypes() {
    }

    /** Idempotent: safe if common setup somehow runs more than once. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        // The amount argument carries no state of its own (a singleton), so the
        // context-free serialiser is the right one: it writes nothing to the wire and
        // the client reconstructs it from the same supplier.
        ArgumentTypeInfos.registerByClass(MoneyArgumentType.class,
                SingletonArgumentInfo.contextFree(MoneyArgumentType::money));
    }
}
