/**
 * The network channel.
 *
 * <p>网络通道。
 *
 * <p>One class, and one rule: register packets by appending. Message ids are assigned by a counter as registration runs, so inserting in the middle renumbers everything after it.
 *
 * <p>一个类，一条规矩：新增网络包只能往后追加。消息 id 是按注册顺序用计数器分配的，在中间插入会让它后面所有的 id 全部改变。
 */
package com.example.globalshop.core.network;
