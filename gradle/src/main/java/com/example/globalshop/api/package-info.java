/**
 * The stable surface GlobalShop offers to other mods.
 *
 * <h2>The contract</h2>
 *
 * <p>Everything in this package and its subpackages is <b>supported</b>. It keeps working across 1.x
 * releases, and anything removed or changed here is a breaking change that will be announced and
 * versioned. Everything outside it — {@code internal.*} — is free to change between releases without
 * notice. An addon that reaches into {@code internal} will break when it does, and that is the whole
 * reason this package exists.
 *
 * <h2>How to use it</h2>
 *
 * <p>Start at {@link com.example.globalshop.api.GlobalShopApi}. It hands out the economy and the
 * registry for page two's places, and it is the only class an addon needs to name at startup.
 *
 * <pre>{@code
 * // Money, through the same door the mod uses itself.
 * EconomyService economy = GlobalShopApi.economy(server);
 * if (economy.hasEnough(player.getUUID(), 500)) {
 *     economy.removeBalance(player.getUUID(), 500);
 * }
 *
 * // A place on the second page of the market window's column.
 * FeaturePlaces.register(0, FeaturePlaces.of(
 *         Component.literal("My Feature"), icon, player -> openMyScreen(player)));
 * }</pre>
 *
 * <h2>What an addon may rely on</h2>
 *
 * <ul>
 *   <li>The economy's atomicity: a transfer either moves the money or does nothing, and a balance
 *       never goes negative. This is not a promise the implementation tries to keep; it is the
 *       reason the implementation is written the way it is.</li>
 *   <li>That every successful balance change is announced to the player's client, which is why an
 *       addon never has to send a balance update itself.</li>
 *   <li>That a registered place keeps its index and is only ever run on the server, on the server
 *       thread, with the player who clicked it.</li>
 * </ul>
 *
 * <h2>What an addon must not do</h2>
 *
 * <ul>
 *   <li>Write a balance without going through {@code EconomyService}. Nothing else announces the
 *       change, so the player's screen would silently disagree with the server.</li>
 *   <li>Assume an index is free. Check {@link com.example.globalshop.api.feature.FeaturePlaces#isTaken}
 *       or read the return value of {@code register}.</li>
 *   <li>Change an existing translation key. Add one.</li>
 * </ul>
 *
 * @since 1.1.0
 *
 * <h2>中文说明</h2>
 *
 * <p>本包及其子包是 GlobalShop 对外承诺的接口：它们在 1.x 各版本之间保持可用，
 * 移除或修改其中任何东西都属于破坏性变更，会提前说明并升版本号。
 * 本包之外的一切（{@code core.*}、{@code feature.*}、{@code internal.*}）都可能在版本之间改变 ——
 * 这正是这层拆分存在的理由。
 *
 * <p>入口是 {@link com.example.globalshop.api.GlobalShopApi}：它给出经济、第二页的位置注册表、
 * 模组事件，以及版本号。附属模组在启动阶段只需要认识这一个类。
 *
 * <p>两条不能违反的规矩：不要绕过 {@code EconomyService} 直接写余额（否则玩家的界面会和
 * 服务端悄悄不一致），不要改动已有的翻译键（要加就加新的）。
 */
package com.example.globalshop.api;
