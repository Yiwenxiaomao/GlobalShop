/**
 * The money: the contract, the locator, the result and the formatting.
 *
 * <p>钱：契约、定位器、结果和格式化。
 *
 * <p>The same instance the mod's own market, bank and auctions use. Every successful change is announced to the player's client from inside the implementation, which is why an addon never sends a balance update itself.
 *
 * <p>模组自己的市场、银行、拍卖用的就是同一个实例。每一次成功的变动都会在实现内部主动通知玩家客户端，所以附属模组永远不需要自己发余额包。
 */
package com.example.globalshop.api.economy;
