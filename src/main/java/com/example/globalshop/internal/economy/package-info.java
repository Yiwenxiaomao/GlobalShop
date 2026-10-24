/**
 * The bundled economy and the save file behind it.
 *
 * <p>内置经济，以及它背后的存档。
 *
 * <p>All validation and mutation happens inside one synchronized block on the saved data, which is what makes a transfer all-or-nothing.
 *
 * <p>所有校验和修改都在存档对象上的同一个 synchronized 块里完成，这正是转账「要么全成、要么全不成」的原因。
 */
package com.example.globalshop.internal.economy;
