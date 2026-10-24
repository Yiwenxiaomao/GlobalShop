# Working on GlobalShop

For anyone adding a feature to this mod or writing something alongside it. It is written to be read
before the code, because the code follows rules that are not obvious from any single file.

---

## 1. The shape of the source

```
com.example.globalshop
├── GlobalShop              the @Mod entry point
├── Config                  every setting, in one file with one static getter each
├── api/                    the supported surface. An addon compiles against this and nothing else
│   ├── GlobalShopApi       the entry point: economy, places, version
│   ├── economy/            the money: interface, locator, result, formatting
│   ├── feature/            page two's thirty-two places, their packets and the client's copy
│   └── event/              the mod's own events
├── core/                   the 1.0.0 mod
│   ├── market/             the fixed-price shop: service, data, result, packets, screen
│   ├── social/             mailbox, blacklist, direct messages, trade offers
│   ├── servershop/         the server's own shop, its clock and its tabs
│   ├── vip/                the VIP window and its packets
│   └── menu/  registry/  command/  event/  network/  client/
├── feature/                one package per 1.1.0 feature, each owning everything it needs
│   ├── auction/            service · data · result · listing · viewers · events · net/ · client/
│   ├── bank/  checkin/  pvp/  teleport/  title/   the same shape
│   └── ranking/  tradelog/                        and these two
├── internal/               implementation that is not part of the promise
│   └── economy/            the bundled economy, its saved data and its packet
└── test/                   SelfTest, the whole suite in one class
```

**The rule is one line: a feature owns its files.** Everything about the auction — its service, its
saved data, its result enum, its packets and its screen — is inside `feature/auction/`. Adding a
fifth thing to the bank means opening `feature/bank/` and nothing else. Before the reorganisation
those pieces were spread across three unrelated directories, which is why this chapter exists.

`api/` is the other half of the rule, and the only part with a promise attached: it is what an addon
compiles against. Its package documentation says what is supported. Everything outside it may change
between releases. **Section 6** is the chapter for addon authors.
---

## 2. The pattern every feature follows

Read one feature end to end before writing another. `bank/` is the smallest complete example.

### The five files

| File | What it is | What to copy from it |
|---|---|---|
| `XSavedData` | Persistence. Extends `SavedData`, one instance per server, saved into the world. | `BankSavedData` |
| `XService` | All the rules. Nothing else decides anything. | `BankService` |
| `XServices` | Hands out the per-server singleton and releases it on shutdown. | `BankServices` |
| `XResult` | One enum of outcomes, each with a translation key. | `BankResult` |
| `XActionPacket` / `XDataPacket` | The two directions of the network conversation. | `BankActionPacket`, `BankDataPacket` |

Plus a screen in the feature own `client/` package, and a line in `core/client/ClientScreens`.

### The rules those files follow

* **Validate, then mutate, all inside `synchronized (data)`.** A service method that reads a balance,
  decides, and then writes it is a race. Every one of them looks like this:

  ```java
  BankSavedData data = data();
  synchronized (data) {
      if (amount <= 0) return BankResult.INVALID_AMOUNT;
      if (!economy().hasEnough(player.getUUID(), amount)) {
          return BankResult.INSUFFICIENT_FUNDS;
      }
      economy().removeBalance(player.getUUID(), amount);
      account.addDeposit(amount);
      data.setDirty();
  }
  return BankResult.SUCCESS;
  ```

* **Never reuse another feature's `Result` enum.** Each feature gets its own, so that adding an
  outcome to one cannot change what another means.

* **The screen never decides.** It sends an action; the server re-checks everything and replies with
  the data packet. A screen that refuses locally is a screen that will one day disagree with the
  server.

* **Nothing is trusted from the client.** Amounts are parsed on the server with the server's own
  currency settings, and re-checked against the wallet, the deposit or the limit.

* **Everything a player reads has a translation key**, in both `en_us.json` and `zh_cn.json`, with
  the same keys in each. `tools/check_i18n.py` fails if they differ.

---

## 3. Recipes

### Adding a feature to the left column

`core/menu/ShopLayout.java` holds one constant per slot (`BANK_BUTTON_Y` and so on). Add one, create the
button in `ShopScreen.init`, add it to the `columnPageOne` list, and set its visibility in
`refreshWidgets`. The three tabs and the pager decide visibility there and nowhere else.

### Adding a place on page two

There are thirty-two places, built in `ShopScreen.buildPageTwoCells`, and they currently do nothing.
Give one an action and it is a feature; the button, the hover, the click and the slot-hiding are
already done. Send an action packet from the button's lambda and reply with a data packet.

### Touching money

Money goes through `internal/economy/InternalEconomyService` and nowhere else. It has one important property:
**every successful change announces itself to the client.** Call `setBalance`, `addBalance`,
`removeBalance`, or `transfer`, and the number on the player's screen updates by itself. Do not write
a balance directly and do not send a balance packet yourself — the refresh button exists for
historical reasons and is not part of a working flow.

### Adding a config option

`Config.java` holds the value as a `ForgeConfigSpec` field, a static getter with a fallback for the
null case, and a comment that says *why* the default is what it is. Then add a row to the table in
`GUIDE.md` **and** `GUIDE.zh_cn.md` — `tools/check_docs_against_code.py` compares them against the
code and fails if a key is undocumented.

### Adding a command

`core/command/ShopCommand.java`, as a subcommand of `/globalshop`. The literal has to appear in the guide
too, for the same checker.

### Adding a packet

`core/network/ModNetwork.java` registers two per feature, action and data. **Add at the end.** Message ids
are positional — they are assigned by a counter as registration runs — so inserting in the middle
renumbers everything after it and a client and server built from different revisions will disagree.

---

## 4. The things that will bite you

| Invariant | Why |
|---|---|
| **A `SavedData` name is permanent.** | The string passed to `computeIfAbsent` is the file name inside the world save. Changing it does not migrate anything; it silently starts from empty and the old file is orphaned. |
| **Packet ids are positional.** | Registration order assigns them. Append, never insert. |
| **Changing a packet's shape means bumping `PROTOCOL_VERSION`.** | Forge checks that string on both ends, and it is the only thing that turns a mismatched client and server into a refusal at login. Without a bump they connect happily and the disagreement surfaces later as the client reading past the end of a packet — a player dropped with an internal exception, and a server log with nothing in it. |
| **Money announces itself.** | See above. A feature that moves money without going through the economy service will look broken in a way that is hard to trace. |
| **Bank locks before economy, never the other way.** | That is the only order used anywhere, which is what makes deadlock impossible. Keep it. |
| **`Slot.isActive()` is how slots are hidden.** | The container draws every slot it has and consults `isActive`; there is no other switch. Painting over slots does not work — the items are drawn by the container after your painting. |
| **Page two's cover is painted in `renderBg`.** | Before the container draws its widgets, so the buttons land on top. Painting it later covers the buttons, which is exactly the bug that took several rounds to find. |
| **The layout is audited.** | `tools/make_gui_preview.py` reads positions out of the Java and reports overlaps and out-of-bounds boxes. A window whose numbers are inline literals cannot be audited, so keep them in named constants. |
| **Never edit a translation key.** | Players' settings, other mods' integrations and the guide all refer to them. Add a new one. |

---

## 5. Before you call it done

```bash
python tools/check_i18n.py             # nothing hardcoded, both languages complete
python tools/check_docs_against_code.py # every config key and command is documented
python tools/check_guide_parity.py      # GUIDE.md and GUIDE.zh_cn.md still match
python tools/make_gui_preview.py        # no overlaps, nothing outside its window
..\_toolchain\gsbuild.cmd runServer --console=plain --no-daemon --offline   # the self-test suite
..\_toolchain\gsbuild.cmd build --console=plain --no-daemon --offline
```

The suite is the contract: **645 checks, and it must stay at zero failures.** It runs against a real
server, so it catches things a unit test cannot — escrow that destroys an item, a duel that leaves
somebody in debt, a balance that does not refresh. If you add a feature, add its checks; if a check
fails, the check is usually right and the code is usually wrong. That has been true every time so
far, including the times it was inconvenient.

Two habits worth copying from it: it **empties and restores** the players it uses, because the suite
runs twice in a row and saved data persists between runs; and it **fails loudly** on a missing
anchor rather than skipping, because a check that silently does not run is worse than no check.

---

## 6. For addon authors: the supported surface

Everything under `com.example.globalshop.api` is a promise. Everything outside it is an implementation
detail that may change in any release, which is why the split exists: without it, an addon has to
guess which of a hundred and fifty-seven classes are safe to touch.

### What is in it

| Class | For |
|---|---|
| `api.GlobalShopApi` | The entry point. Start here and you will not need to name anything else at startup. |
| `api.economy.EconomyService` | Money. The same instance the mod's own market, bank and auctions use. |
| `api.economy.EconomyServices` | Locating that instance, and replacing it with your own implementation. |
| `api.economy.TransactionResult` | What a money operation did, including why it refused. |
| `api.feature.FeaturePlace` | One of the thirty-two places on page two. |
| `api.feature.FeaturePlaces` | Registering one, and checking whether an index is free. |
| `api.event.BalanceChangedEvent` | Posted after any balance change, by anybody. |
| `api.event.FeaturePlaceUsedEvent` | Posted before a place runs. Cancelable. |

### Paying and charging

```java
EconomyService economy = GlobalShopApi.economy(server);
if (economy.hasEnough(player.getUUID(), price)) {
    TransactionResult result = economy.removeBalance(player.getUUID(), price);
    if (result == TransactionResult.SUCCESS) {
        // The player's client is already showing the new figure. Do not send one yourself.
    }
}
```

Every successful change posts `BalanceChangedEvent` and tells the player's client, from one place
inside the economy. An addon that writes a balance some other way breaks both, silently.

### Claiming a place on page two

```java
FeaturePlaces.register(3, FeaturePlaces.of(
        Component.translatable("mymod.place.mine"),
        new ItemStack(Items.DIAMOND_PICKAXE),
        player -> player.sendSystemMessage(Component.literal("Hello from my mod"))));
```

Indexes run 0 to 31, left to right and then top to bottom. `register` returns false if the place is
taken rather than replacing its owner — two mods wanting one index is a bug in one of them, and losing
quietly would hide it. Registration happens during mod setup; the client is told on login.

**Only the index travels when a place is clicked.** The server looks up what the place is and runs it,
so a place is written once, on the side that decides.

### The two rules that will bite you

* **Do not call the bank (or any other feature) from inside a `BalanceChangedEvent` listener.** The
  event is fired after the balance lock is released precisely so that this is safe, but the reverse
  ordering is not: bank takes its lock then the balance lock, and reaching for the bank from a balance
  callback would be the other way round.
* **Do not add or move a packet in the middle of `ModNetwork`'s registration.** Message ids are
  positional. Append only, or a client and a server built from different revisions will disagree about
  what packet three is.
