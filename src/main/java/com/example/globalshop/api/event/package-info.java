/**
 * The mod's own events, for an addon that wants to react to something.
 *
 * <p>模组自己的事件，给想对某些事做出反应的附属模组用。
 *
 * <p>Fired on the Forge event bus. BalanceChangedEvent is posted after any balance change by anybody; FeaturePlaceUsedEvent is cancelable and fires before a place runs.
 *
 * <p>在 Forge 事件总线上触发。BalanceChangedEvent 在任何来源的余额变动之后触发；FeaturePlaceUsedEvent 可取消，在位置被执行之前触发。
 */
package com.example.globalshop.api.event;
