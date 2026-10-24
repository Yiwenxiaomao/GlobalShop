# GlobalShop

> **English** | [简体中文](README.zh_cn.md)

A **server-authoritative shared marketplace** for Minecraft Java Edition 1.20.1 (Forge),
with a built-in economy that does not depend on Vault or any other economy mod.

Every player on the server trades in **one** market. Balances, listings, escrowed stock
and ownership all live in the **server's world save**. The client only draws what the
server sends it.

> **Just want to use it?** Read the [user guide](GUIDE.md) instead — it covers
> installation, first-time setup for an operator, every player-facing feature, the full
> command table, every config key, and the design trade-offs. This README is the
> per-version change log: what was added, and why each decision was made.

---

## 1. Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 (the 1.20.1 *recommended* build). `mods.toml` declares `versionRange="[47,)"`, so newer 47.x builds such as 47.4.23 also load this jar unchanged. |
| Java to run | **17 or 21**, either works. The mod is a Java 17 project (`java.toolchain.languageVersion = 17`), so the class files are Java 17 bytecode and run on any newer JVM. |
| Java to build | JDK 17 (Gradle 8.1.1 does not support running on Java 21) |

---

## 2. Building

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

The jar lands in `build/libs/globalshop-1.1.0.jar`.

> **If `gradlew` cannot download Gradle**, the wrapper points at a mirror because
> `services.gradle.org` redirects to GitHub, which is unreachable from some networks.
> Edit `gradle/wrapper/gradle-wrapper.properties` and change `distributionUrl` to
> whichever of these you can reach:
> * `https://services.gradle.org/distributions/gradle-8.1.1-bin.zip` (official)
> * `https://mirrors.cloud.tencent.com/gradle/gradle-8.1.1-bin.zip` (the default here)
>
> If you already have Gradle 8.1.1 installed you can just run `gradle build` and skip
> the wrapper entirely.

---

## 3. Installing

Copy `globalshop-1.1.0.jar` into the `mods/` folder.

### Client vs server — this matters

GlobalShop is **server-authoritative**. Data is stored by the server, and every
decision (prices, stock, balances, ownership) is made by the server.

* **Multiplayer server:** the mod **must be installed on the server**. A client that
  has the mod but connects to a server without it cannot open the market, cannot use
  the commands, and cannot change a balance — the server simply does not know the
  commands or the packet channel.
* **Clients:** install the same version of the jar, because Forge checks the network
  protocol version and the mod must exist on both sides for the GUI and the item to
  appear. A client without the mod sees an unknown item and an unopenable block; it
  cannot use the market.
* **Single player / LAN:** the integrated server is the authority, so data still goes
  into that world's own `data` directory — never into a global client location.

---

## 4. Where the data lives

This is a hard design constraint, not an implementation detail.

| What | Where | Notes |
|---|---|---|
| Balances | `<world>/data/globalshop_economy.dat` | Server-side `SavedData`. Keyed by **player UUID**, so renaming a player cannot lose their money and offline players keep their balance. |
| Listings, escrowed stock, ownership, mailbox | `<world>/data/globalshop_shop.dat` | Same mechanism. Shared by the whole server, included in world backups. |
| Trade log | inside `globalshop_shop.dat` | Capped at `tradeLogRetention` entries, oldest dropped first. |
| Policy (starting balance, price limits, …) | `config/globalshop-common.toml` | **Policy only.** No balance, stock or ownership is ever stored in a config file, so editing it cannot invent money or items. |

On a dedicated server the path is `<server root>/world/data/globalshop_*.dat`. On a
single-player or LAN world it is `<saves>/<world name>/data/globalshop_*.dat`.

**Nothing authoritative is ever written to a client.** The client holds a display cache
that is replaced on every page request and discarded when you disconnect, so
reconnecting always starts from the server's state.

### Ownership and identity

Accounts are keyed by UUID. Names are recorded only so command output and log lines can
show something readable; they are never used as an identity.

---

## 5. Commands

| Command | Permission | What it does |
|---|---|---|
| `/money` | everyone | Show your own balance. |
| `/pay <player> <amount>` | everyone | Pay another player. Refuses zero, negative, self-payment, and amounts above your balance. |
| `/money <player>` | OP (level 2) | Show another player's balance. Works for offline players. |
| `/money give <player> <amount>` | OP (level 2) | Add money. |
| `/money take <player> <amount>` | OP (level 2) | Remove money. Refuses to go below zero. |
| `/money set <player> <amount>` | OP (level 2) | Set an exact balance. |
| `/shop` | everyone | Open the market. |
| `/globalshop` | everyone | Open the market. |
| `/globalshop open` | everyone | Open the market. |
| `/globalshop claim` | everyone | Collect items owed to you (returns that arrived while you were offline). |
| `/globalshop money …` | as `/money` | Fallback if another mod already owns `/money`. |
| `/globalshop pay …` | as `/pay` | Fallback if another mod already owns `/pay`. |
| `/globalshop reload` | OP (level 2) | Re-apply config and persist the market. |
| `/globalshop logs [count]` | OP (level 2) | Show recent trade log entries. |
| `/globalshop ids [count]` | OP (level 2) | List listing ids, so `/globalshop remove` is usable. |
| `/globalshop remove <id>` | OP (level 2) | Force-remove a listing. Unsold items go back to the seller, who may be offline. |
| `/globalshop selftest` | level 4 | Run the built-in verification (see §8). |
| `/globalshop market` | everyone | Open the player market. |
| `/globalshop server` | everyone | Open the official server shop. |
| `/globalshop server add <item> <sell> <buyback> <bundle> [limit] [recycle]` | OP (level 2) | Stock a product. |
| `/globalshop server remove <item>` | OP (level 2) | Take a product off the list. |
| `/globalshop server list [count]` | OP (level 2) | Show the catalogue. |
| `/globalshop server reload` | OP (level 2) | Re-apply the time settings; persist. |
| `/globalshop server resetDaily <player>` | OP (level 2) | Clear one player's sell-back tally (debugging). |
| `/globalshop server logs [count]` | OP (level 2) | Recent server shop trades. |

**Who can mint money.** Only an operator: `/money give`, `/money take`, `/money set` and
`/money <player>` are all level 2, on **both** of their paths (`/money …` and
`/globalshop money …`). This is not asserted from reading the source — the self test walks
the command tree the server actually built and evaluates each node's requirement against
real sources at permission levels 0, 2 and 4, then checks that nothing a player can reach
is named `give`, `take` or `set` (§8). The economy commands are registered twice through a
shared builder, so a check that reached only one of those paths would have been a
privilege-escalation hole; the test is what rules that out.

What a normal player can reach in this mod is exactly:

```
/money          /pay <player> <amount>      /shop        /globalshop
/globalshop open   /globalshop claim   /globalshop money   /globalshop pay <player> <amount>
```

`/pay` being open is intended — it is a transfer out of the sender's own balance, it
refuses self-payment, and it is one atomic debit-and-credit, so a player cannot use it to
create money. (`/pay` and `/globalshop pay` with no arguments are traversable but have no
executor, so Brigadier reports them as incomplete rather than running anything.)

**Amounts** accept both plain and decimal notation: `10`, `12.34`, `.5`, `1,234.50`.
They are parsed with exact decimal arithmetic into the smallest currency unit, so a
command can never introduce floating-point rounding.

---

## 6. The marketplace

**Getting a terminal.** The *Global Shop Terminal* is in the GlobalShop creative tab.
Right-click the ground to place it, then right-click the block to open the market.
No tool or empty hand is required — right-clicking works whatever you are holding,
which is the usual convenience for a shop block. Holding sneak is passed through
instead, so you can still place blocks against the terminal when you need to.

**Selling.** Put the stack you want to sell into the **Sell slot** — it sits in its own
compartment to the right of your backpack, and it is drawn in every tab so it is never a
guess where it is. Then open the *Sell* tab and set:

* unit price
* stock (the total number put up for sale)
* minimum per purchase
* maximum per purchase

The stock is then **escrowed**: the items are taken out of the sell slot and your backpack
immediately and held by the shop. A listing can therefore never pay out items the seller
did not actually hand over. Listing more than one stack works normally — the slot is drawn
from first and the backpack makes up the rest — and the stock box is pre-filled with
everything of that item you are carrying, so "sell all of it" needs no arithmetic.

**Buying.** Click a lot in the grid. The right-hand panel shows the seller, the unit
price, the remaining stock, the minimum and maximum per purchase, the listing time, and
a live **`unit price x quantity = total`** line. Adjust the quantity with `-` / `+` or by
typing, then press *BUY*.

For an item where it matters, the panel describes the item itself, not just its name:
**remaining durability** as a colour-coded `312/1561` readout, and **the enchantments** on
one line, with a `(+n)` count when more do not fit — a bare "..." would leave you unable to
tell a one-enchantment sword from a five-enchantment one.

Hover **any lot in the grid, or the item icon in the panel**, to get the complete vanilla
tooltip: the same box the inventory shows, built from `ItemStack.getTooltipLines`. Name
with its rarity and enchantment styling, every enchantment, durability, attribute
modifiers, and any lines a modded item adds — so items from other mods describe themselves
with no work from this mod at all.

**Taking a lot down again — including by mistake.** Select any lot of your own and the
panel's big button becomes ***UNLIST*** instead of *BUY*, in the market tab as well as in
*My Listings*. There is no state in which you can see your own lot and not be able to take
it down: buying your own lot is meaningless, so that button slot always offers the action
that makes sense. Unsold escrow goes straight back to your inventory — or to your mailbox
if you are offline, from where `/globalshop claim` collects it.

*My Listings* additionally lets you change price, stock, minimum and maximum. Raising the
stock escrows the extra items from your backpack first, so you cannot inflate a lot beyond
what you own; lowering it returns the difference. A sold-out lot disappears from the public
market but stays in your own tab so you can restock or delete it.

**Paging, sorting, searching.** The grid is 6x6 = 36 lots per page with `<` / `>`
buttons (the mouse wheel works over the grid too). Sort by price (ascending or
descending), by listing time (newest or oldest), or by seller. Type in the search box to
filter by item name, registry name or seller — it is debounced, so it does not send a
packet per keystroke.

### The official server shop

A second shop, for a second purpose. The player market moves items *between players*; the
server shop buys from and sells to players out of nothing, against a price list an operator
writes. They are different blocks, different items, different menus and different screens —
and one economy. The balance in the server shop's header is the same balance `/money` prints
and the market spends, because all three go through one `EconomyService`. There is no second
currency and no second set of accounts.

**Two blocks, not one.** `globalshop:shop_terminal` opens the market;
`globalshop:server_shop_terminal` opens the server shop. Both are in the mod's creative tab,
and neither is craftable — buying from the server shop is the only way to obtain one.

By default the server shop stocks exactly one line:

| Product | Default price | Limit |
|---|---|---|
| `globalshop:shop_terminal` — the player market terminal | 1000.00 | **10 per player** |

The market terminal is the **rationed** one: it is the block that lets a player sell to everyone
else, which is worth rationing. This shop's own terminal is **not stocked at all** — it is only a
way in, and an operator can place one or open the shop with `/globalshop server`. Both lines are
configurable (`sellMarketTerminal` / `stockOwnTerminal`, with their prices and limits).

Which built-in lines exist is **reconciled from the config on every start**, rather than recorded
as a one-shot "already seeded" flag. A one-shot migration was tried first and was not good
enough: it marked a world as up to date on a run where the config still asked for a line, so the
correction was spent without taking effect and never came round again. Prices are written only
when a line is first created, so an operator's edits are never overwritten; the cost is that
removing a built-in line by hand undoes itself on the next start, and the config flag is the
supported way to be rid of it. `ServerShopSavedData.DEFAULTS_VERSION` is kept as a record of
which defaults a world has seen.

### Three pages, because they are three jobs

Selling and buying back are **separate pages**, because they are opposite transactions with
opposite rules — buying has no limit and costs money; selling back is capped by a daily
allowance and earns money:

* **Buy** — the price, the per-purchase bundle, the purchase limit, and a live
  `unit price x quantity = total`.
* **Sell back** — the buy-back price, what the player is carrying, the allowance used and
  remaining, and what selling everything held would actually pay.
* **Manage** (operators only) — where the catalogue is written.

Each trading page lists only what it can actually do: a line with no buy-back price is absent
from the sell-back page, and a line with no sale price is absent from the buy page.

### Sale-only, buy-back-only, or both

A line may be any of the three. **A sale price of zero makes it recycling-only** — something the
server takes in and never hands out, for example plain cobblestone an operator wants to soak up.

Two bugs came from getting this wrong, and both are worth knowing about because they were the
same mistake twice. First, requiring a sale price above zero made a recycling-only line
impossible to *stock*. Then, once it could be stocked, the rule that a buy-back price must
undercut a sale price was still being applied to it — comparing 20 against a sale price of 0,
concluding 20 was not below 0, and refusing every attempted sale. The line existed and could
never be used, which is exactly what a player reported.

That rule now lives in **one method**, called from both the place a line is created and the place
it is traded, and it deliberately does not apply when there is no sale price to undercut. A
buy-back-only line is also unbuyable, so there is nothing there to exploit.

### Writing the catalogue from the window

The management page takes the item from a **product slot**, dragged in from the player's hotbar,
which the page shows for exactly that reason. An operator who kept the item in hand is accepted
too, so neither route is a dead end. Stocking never consumes the item, and closing the window
hands anything left in the slot straight back.

There is room for **one** inventory row and not four: nine slots are 162 pixels wide, and the
320-pixel window is capped by the 240-pixel height that fits an 854x480 screen. Four rows would
need 72 pixels of the 26 available between the panel and the action buttons. One row reaches the
hotbar, which is where the item being worked on usually is; an operator whose item is elsewhere
can hold it instead.

The two textures are colour-inverted copies of one another, so they cannot be confused in a
hotbar. Measured from the shipped files rather than described from memory: the market
terminal is a cool grey body (`#181A20`) with a flat light-blue frame (`#4FC3F7`) around a
warm gold core, and the server terminal swaps that to a warm grey body (`#231D1C`) with a
flat gold frame (`#FFE05E`) around a cool blue core.

**Buying is unlimited.** The shop's stock is infinite, so nothing counts down. The only
ceiling is a line's optional purchase limit, counted per player UUID and persisted.

**Selling back is not.** This is the direction in which a player can turn items into money
without another player's consent, so two rules guard it:

* the sell-back price must be **strictly below** the purchase price — checked when a line is
  added *and* again on every trade, because it is the rule that stops money printing;
* each player has a **daily allowance**, 128 items by default, counted in items and shared
  across every line. Buying has no such limit.

**The daily allowance follows the real calendar.** Not the world's day count, not the tick
count, not uptime — those all stop advancing while the server is down, which is exactly the
case that matters. Each player stores a `lastRecycleDate` (a `yyyy-MM-dd` string) beside
their tally, and every read and write compares it with today's date before deciding
anything. So the reset needs no timer and no catch-up pass: it happens on first contact
after midnight, whether the server ran through midnight, was restarted the next morning, or
was switched off for a week. The zone is configurable (`serverShop.timezone`), and an
unknown zone falls back to the server's own with a warning rather than breaking recycling.

Optionally the date can come from an NTP server instead (`serverShop.useOnlineTime`). The
lookup runs **in the background and is cached** — never on the trading path, where a slow or
unreachable time server would stall the server thread. If it fails, the system clock is used
and a warning is logged; recycling keeps working either way.

The window shows the three purchase numbers the operator set (bought / limit / still
available) and the three quota numbers (used / limit / still sellable), plus the item's own
durability and enchantments, with the full vanilla tooltip on hover.

### Compatibility with items from other mods

**Yes.** A lot is stored as a registry name plus the item's full NBT, so any registered
item works. Nothing checks for a specific vanilla item, and the search box also matches
the registry name, so typing `create` lists that mod's goods.

Five things are worth knowing:

* **A lot must be made of byte-identical copies.** Escrow matches with
  `ItemStack.isSameItemSameTags`, so two items differing in any tag are different items.
  A 50%-durability pickaxe and a full one cannot be pooled into one lot of two, and
  neither can two backpacks with different contents. This is deliberate: it is what stops
  a cheap item being substituted for an expensive one.
* **A lot stores a template plus a count.** Every copy a buyer receives is an identical
  clone of the template. That is exactly right for plain stackables; for an item whose
  every copy is meant to be unique (randomised affixes, for example) all copies will
  carry the template's values rather than their own.
* **State kept in a capability rather than in NBT is not preserved.** That is a property
  of `ItemStack.save`, not of this mod, but it affects the same class of item.
* **Removing the mod that supplied an item makes its lots unresolvable.** On load an item
  id that no longer exists resolves to air, so the listing — and any mailbox entry — is
  dropped, and the escrowed items go with it. Back the world up before uninstalling a
  content mod.
* **There is no item blocklist yet.** `ShopResult.FORBIDDEN_ITEM` is declared but never
  returned, so anything a player can hold can be listed. A config- or tag-driven
  blacklist is the natural addition if you need one.

---

## 7. Configuration — `config/globalshop-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `economy.initialBalance` | `0` | Balance given the first time a player is seen. Applied **once**; relogging cannot farm it. `0` disables it. |
| `economy.maxBalance` | `1000000000000` | Hard cap on any single balance. Operations that would exceed it are refused, never clamped. |
| `economy.minorUnitsPerMajor` | `100` | Minor units per major unit. `100` displays `1234` as `12.34`. Only powers of ten are honoured. |
| `economy.currencyName` | `"Gold"` | Shown after every amount. |
| `shop.allowListing` | `true` | If false, only operators may list (everyone can still buy). |
| `shop.maxListingsPerPlayer` | `20` | Active lots per player. |
| `shop.maxUnitPrice` | `1000000000` | Highest price per single item, in minor units. |
| `shop.maxStock` | `1000000` | Highest stock per listing. |
| `shop.maxPerPurchase` | `4096` | Ceiling for a listing's "maximum per purchase". |
| `shop.purchaseCooldownMs` | `150` | Minimum gap between a player's purchase requests. `0` disables. |
| `shop.tradeLogRetention` | `2000` | Log entries kept. |

---

## 8. Testing

### Automated: the built-in self test

The mod can verify itself against a **real running server**. It uses Forge's
`FakePlayerFactory` to get genuine `ServerPlayer` instances, so it exercises exactly the
code that handles a real purchase.

**In game** (level-4 operator):

```
/globalshop selftest
```

**Headlessly**, which is useful in CI: start the dedicated server with the system
property set and it runs the checks on startup and then stops the server.

The property has to reach the **server's** JVM, not Gradle's daemon. Either:

```groovy
// build.gradle, in the "server" run block
server {
    jvmArgs '-Dglobalshop.selftest=true'   // already present there, commented out
}
```

then

```bash
./gradlew runServer
```

or set it for the launched JVM without editing the build:

```bash
# Linux / macOS
JAVA_TOOL_OPTIONS=-Dglobalshop.selftest=true ./gradlew runServer

# Windows PowerShell
$env:JAVA_TOOL_OPTIONS = "-Dglobalshop.selftest=true"; ./gradlew.bat runServer
```

The result appears in the server log as a series of `[selftest] PASS/FAIL` lines ending
in `RESULT n passed, m failed, k skipped`. A clean run reports
`RESULT 544 passed, 0 failed, 0 skipped`. The test is repeatable: it removes the listings
it creates, drains the mailboxes it used and restores the balances it touched, so running
it twice against the same world gives the same result. It does leave two reserved
accounts (`GS_Alice`, `GS_Bob`) with a zero balance behind.

> To run `runServer` you must accept the Minecraft EULA yourself by creating
> `run/eula.txt` containing `eula=true`.

#### What the self test checks

Money arithmetic: balances read back; `hasEnough` at and above the balance; zero and
negative amounts rejected; overdrawing refused **with the balance unchanged**; a
transfer debits and credits together; self-payment refused; an unaffordable transfer
refused **with both balances unchanged**; the sum of both balances is conserved; an
overflowing credit is refused rather than wrapped.

Marketplace: listing escrows exactly the listed quantity; the lot appears for other
players and is findable by name; quantities below the minimum, above the maximum, above
the stock, on your own lot, and on an unknown lot are each refused with a specific
reason; a purchase moves exactly the right money and exactly the right items; a purchase
without funds changes neither money, nor stock, nor goods; price edits apply; a
contradictory limit set is refused; a non-owner cannot edit; unlisting returns the
remaining escrow.

Persistence: balances, the account list, the listing count, the trade log, a listing's
item, its stock, its price and its ownership all survive a serialise/re-read round trip,
which is exactly what a server restart does.

Items carrying NBT: a sword with a custom name, two enchantments and a damage value is
listed, stored, round-tripped and bought, and the buyer receives a stack whose tags are
byte-identical. This is the mechanism modded items depend on — plain stone has no NBT, so
it would pass even if every tag were dropped. The same lot is also pushed through the real
packet codec, which is the only check that exercises the mod's network serialisation, and
which proves the detail panel will receive the enchantments and the damage value it needs
to display them.

Throttle: a rapid second purchase from the same player is rejected as `COOLDOWN`.

Command-tree serialisation: every argument type in the command tree is registered for
client sync, and the whole tree is actually serialised through the same call the server
makes while a player is joining. This section exists because of a bug that once shipped —
an unregistered custom argument type made `Commands.sendCommands` throw, so **every login
was refused** with "Invalid player data" — and the rest of this test did not notice,
because a fake player never logs in. Do not remove these checks without replacing them
with something that still exercises that path.

Screen payload: the page the GUI is built from marks the viewer's own lots as theirs (the
Unlist button keys off exactly that flag), never marks another player's lots as theirs,
keeps sold-out lots on their seller's own page so they can be restocked or removed, and
hides them from the public market.

Command permissions: the tree the server built is walked with real sources at permission
levels 0, 2 and 4, and every command that moves money is asserted to be out of reach for
a player on both of its registration paths — plus a sweep asserting that nothing a player
can reach is named `give`, `take` or `set`. Reading `.requires(...)` would not have caught
a check that landed on only one of the two paths. Only this mod's own subtrees are walked
in full, and one level of everything else: Brigadier's tree is a DAG with shared nodes, so
enumerating every path through it combinatorially does not terminate — an earlier version
of this walk hung the whole server run until it was bounded.

Escrow across pools: listing 128 items when the sell slot holds 64 and the backpack holds
the other 64 succeeds, takes from both, and leaves nothing behind; asking for one more
than is carried is still refused and changes nothing. This section exists because of a bug
that shipped — escrow matched against the live stack in the sell slot, so emptying that
slot emptied the probe and the backpack matched nothing, making "list more than one stack"
fail with "not enough items" however much the player was carrying. Every earlier test
listed no more than the slot held, so none of them crossed that boundary.

### Manual checklist

Run these on a test world. Two players (or one player and an operator) are enough.

1. **Balance** — `/money` → "Your balance is 0.00 Gold" (or your configured starting balance).
2. **Add money** — `/money give <you> 100` → balance becomes `100.00 Gold`.
3. **Transfer** — `/pay <other> 25` → your balance drops by 25, theirs rises by 25, and the recipient sees a message. Then check the failures: `/pay <other> 0` (rejected), `/pay <you> 5` (rejected), `/pay <other> 999999` (insufficient funds, and **your balance must be unchanged**).
4. **Open the market** — `/shop`, and separately place a terminal and right-click it. Also confirm `/globalshop open` works.
5. **A lists, B sees** — as A, put 64 stone in the sell slot, *Sell* tab, price `1`, stock `64`, min `1`, max `64`, press *List*. Confirm the 64 stone left A's inventory. As B, open the market and confirm A's lot is visible with A's name, the price and the stock 64.
6. **B buys** — B selects the lot, sets quantity 10. Confirm the panel reads `1.00 x 10 = 10.00`. Press *Buy*. Confirm: B's balance dropped by exactly 10; A's balance rose by exactly 10; B has 10 stone; the lot's stock is now 54.
7. **Insufficient funds** — as B, spend down to below the price (or `/money set B 0`), then try to buy. Confirm it is refused, **B's balance is unchanged, and the stock is still 54**.
8. **Purchase-size limits** — with min 2 and max 4: buying 1 is refused ("below the minimum"), buying 5 is refused ("above the maximum"), buying 2..4 works.
9. **Seller edits** — as A, use *My Listings* to change the price to `2.50`. As B, confirm the new price appears after a refresh. Raise the stock and confirm the extra items were taken from A's backpack; lower it and confirm they came back.
10. **Unlist** — as A, unlist. Confirm the remaining items are back in A's inventory and the lot is gone from B's market view.
11. **Server restart** — stop the server, start it again. Confirm `/money` shows the same balances, and the unsold lots are still there with the same price and stock.
12. **Files on disk** — confirm `world/data/globalshop_economy.dat` and `world/data/globalshop_shop.dat` both exist. Nothing economy-related should appear in `config/`.
13. **Server-only enforcement** — connect with a client that does not have the mod and confirm the market cannot be opened.

---

## 9. Replacing the economy

The bundled economy is one implementation of one interface and is deliberately isolated
in its own package.

```
com.example.globalshop.economy
├── EconomyService.java        the contract: getBalance / addBalance / removeBalance /
│                              transfer / hasEnough / setBalance / ensureAccount
├── EconomyServices.java       the seam: install a different implementation here
├── InternalEconomyService.java the bundled implementation
├── EconomySavedData.java      its server-side storage
├── MoneyFormat.java           long minor units <-> text, exact decimal parsing
└── TransactionResult.java     why an operation was refused
```

To substitute your own, implement `EconomyService` and install it during mod setup:

```java
EconomyServices.install(server -> new MyEconomyService(server));
```

Nothing else changes — the shop, the GUI and every command obtain the economy through
`EconomyServices.get(...)`.

Two things a replacement must preserve, because the rest of the mod relies on them:
amounts are **strictly positive** (direction is expressed by which method you call), and
a `transfer` is **atomic** — either both balances change or neither does.

---

## 10. How duplication and free money are prevented

* **Escrow, never a promise.** Listing items removes them immediately. The market can
  only hand out what it actually holds.
* **Validate, then mutate.** Every check happens before the first change, so a refusal
  leaves no partial state to exploit by retrying.
* **One lock per operation.** Check-and-apply runs inside `synchronized (data)`, so two
  concurrent requests cannot both see "1 left in stock" or "balance is sufficient".
* **Money before goods.** A purchase charges the buyer first and delivers only after the
  transfer succeeded, so items can never be handed over unpaid.
* **Exact arithmetic.** Money is a `long` count of minor units everywhere, with
  `Math.addExact` / `Math.multiplyExact` so an overflow is refused instead of wrapping.
* **Untrusted input.** A request carries at most "which listing" and "how many". The
  client never names a price, a seller, a balance or an item, and transaction packets are
  only accepted while the sender actually has the market open.

---

## 11. Languages

`en_us` and `zh_cn` are included. Add another by dropping a file into
`src/main/resources/assets/globalshop/lang/`; every key the mod uses is listed in
`en_us.json`.

---

## 12. Project layout

```
src/main/java/com/example/globalshop/
├── GlobalShop.java          mod entry point
├── Config.java              policy only (see §4)
├── economy/                 the economy package (see §9)
├── shop/                    market model and all trade logic
│   ├── Listing.java         one lot
│   ├── ShopSavedData.java   server-side storage
│   ├── ShopService.java     every mutation, all of it server-side
│   ├── ItemStacks.java      item movement, written to be fail-safe
│   └── ...
├── menu/
│   ├── ShopLayout.java      every window coordinate, shared by the menu and the screen
│   └── ShopMenu.java        container menu (only the sell slot is a real slot)
├── network/                 packets; requests carry intent, never state
├── client/                  ShopScreen, ClientShopCache
├── command/                 /money, /pay, /shop, /globalshop
├── event/ShopEvents.java    commands, login, mailbox, lifecycle
└── test/SelfTest.java       the in-game verification
tools/make_textures.py       regenerates the item and block textures
tools/make_mod_icon.py       draws the 256x256 mod icon (mods.toml logoFile)
tools/make_gui_preview.py    renders the window as an image, for reviewing the layout
tools/check_guide_parity.py  checks the two guides still say the same things
tools/check_docs_against_code.py  checks the guide's commands and config defaults against the code
```

### Keeping the guide true

`tools/check_docs_against_code.py` compares the user guide against the code it documents: every
`/globalshop` subcommand it names must be registered, and every config default it prints must match
`Config.java`. It was written after the guide spent two versions telling people to type
`/globalshop vip info` and `/globalshop unlistall` — commands that did not exist. Run it after
touching the command tree, `Config.java`, or either guide.

`tools/check_guide_parity.py` compares the English and Chinese guides on the parts that must match
regardless of language: every backticked token, every number, the heading count and levels, and the
code blocks. Prose is deliberately not compared.

### Changing the window layout

Edit `menu/ShopLayout.java` (coordinates) and the matching table in
`tools/make_gui_preview.py`, then run that script and look at `tools/gui_preview.png`. It
draws all three tabs at 2x **and prints an overlap audit**. This is not optional
politeness: the preview has found five overlapping-element bugs and a text element
overflowing its panel, all of which a compiler cannot see. Three rules keep it honest:

* every string drawn in the preview must be one the mod actually renders — an earlier
  version drew a shortened balance string and so missed the real one colliding with the
  sort buttons;
* the wells and the panel are tracked as boxes too, so text spilling over an edge is
  reported rather than only element-on-element collisions;
* each tab draws its **worst case**, not an easy one — the market tab shows the viewer's
  own, enchanted, already-used tool, because drawing a plain item is how the durability
  readout and the owned badge came to be stacked on top of each other unnoticed.

## New in 1.1.0

### The auction house

A second way to sell, alongside fixed-price lots: bidders compete, the seller sets a starting price
and a minimum increment, and may add a buyout price that ends it instantly.

* **Bids are deducted immediately.** There is no "reserved" flag: the money leaves the balance and is held by the auction. That is what makes bidding the same money on two auctions impossible, and what makes a crash mid-auction refundable.
* **Being outbid refunds at once**, not when the auction ends.
* **Anti-sniping**: a bid inside the last 30 seconds puts the time left back to 30 seconds, repeating until half a minute passes with nobody bidding. Because the window and the reset are the same length, it can only extend an auction, never shorten one.
* **Everything settles through the mailbox**, never straight into a backpack.
* **One clock**: every packet carries the server's timestamp and each client counts down from the difference, so a wrong machine clock or another time zone changes nothing.

### The mailbox is bounded by time now, not by count

`mailboxLimit` is gone. A mailbox takes whatever arrives and deletes anything older than
`mailLifetimeDays`, so a delivery is never refused for want of room and a box never grows without
end. Expired mail is deleted rather than kept as a greyed-out entry.

### Titles, check-in, teleport, PvP and the bank

What else arrived in 1.1.0, in one place:

| Feature | Where | In short |
|---|---|---|
| **Custom titles** | the *Titles* button | A word in front of your name, up to seven characters, priced by what you type: 50,000 for letters and digits, 250,000 the moment a symbol is involved. |
| **Daily check-in** | the *Check-in* button | One claim a day, weeks running Monday to Monday, the seventh day paying seven times the first. A missed day can be made up at half pay for a flat fee. |
| **Teleport** | the *Teleport* button | A random jump with a safety scan, or a request to a named player that waits for them to accept. Twenty coins, two cooldowns, and the request arrives in chat as buttons to click. |
| **PvP** | the *PvP* button | Duels by consent. The loser pays one to twenty percent of a 30,000 stake and it goes **to the winner** rather than into the void, and nobody can be left in debt by it. |
| **Bank** | the *Bank* button | Deposits earn 0.5% per **real** day; loans cost 2% per **game** day against a fixed ceiling. Interest is added to what is owed and then the wallet is emptied into it, and a balance never goes negative. |
| **The side column turns over** | the arrows at the bottom | Ten features in a column that pages, with a second page of thirty-two places for whatever comes next. |
| **Trade log types** | the *Trade log* | Every entry is tagged by kind, so a purchase, a sale, a commission and an auction settlement can be told apart at a glance. |
| **Balances update themselves** | everywhere | Every change to a balance is pushed to the client, so the number on screen is never one action behind and pressing refresh is not a step anybody has to remember. |

## 13. License

MIT — see [LICENSE](LICENSE).

You may use, modify and redistribute this mod, including inside modpacks and on a server you run
for other people, provided the copyright notice and the licence text stay with it. There is no
warranty of any kind.

The jar declares `license="MIT"` in its `mods.toml`; that value comes from `mod_license` in
`gradle.properties`, so the licence the players see and the one in the repository cannot drift
apart.

## New in this version

### Trading commission
Every sale takes a configurable cut (`marketCommissionPercent`, default 5%) out of the total.
The buyer pays the full price, the seller receives the total less the cut, and **the cut leaves
the server entirely** — it is credited to nobody, so the shop is a money sink rather than a third
account. The seller's notification always states the amount *after* the cut, because that is the
number that matters to them.

### Listings expire
A lot stops being purchasable after `listingLifetimeHours` (default 12) of **real-world** time,
measured against the wall clock rather than game ticks — so a lot expires while the server is
switched off, which is the case that matters. An expired lot disappears from the public market
and cannot be bought, but it stays in the seller's own list, drawn dimmed, with only "unlist"
left to do. Set the lifetime to 0 to switch expiry off.

### Sale notifications survive being offline
When a lot sells, the seller is told what went and what they were paid. If they are offline the
notice is written into the world save and delivered the next time they log in, exactly like the
item mailbox beside it.

### VIP
`/globalshop vip` buys a permanent rank for `vipPrice` (default 10000.00) using the in-game
currency. VIP may have `vipListingLimit` (20) lots on the market at once; everyone else gets
`listingLimit` (10). Operators can hand it out with `/globalshop vip grant|revoke <player>`.
Both the viewer's own rank and the rank of the seller of the selected lot are shown in the market
window, grey for normal and purple for VIP.

### Restocking and bulk unlisting
"My listings" has a **Restock** button that takes everything of that item the seller is carrying
and adds it to the lot — the server counts the items, so there is no number to get wrong — and an
**Unlist all** button that takes every one of their lots down in one request, returning the
escrowed items through the same path a single unlist uses.

### Sounds
A successful trade plays the experience pickup sound; a refused one plays the villager refusal.
The verdict comes from the server's result, so a trade that looked available but was refused
still sounds refused.

## Crashes and restarts

A trade is applied in full or not at all: there is no half-completed state to repair, because
every balance change and stock change belonging to one trade happens inside a single synchronized
block.

What a crash **can** cost is the most recent trade or two. Completed trades live in memory and
reach disk on the world save, so killing the process in the seconds after a trade loses that
trade *whole* rather than leaving it half-applied. This is deliberate: forcing a disk write after
every purchase would make the shop a bottleneck the moment more than a handful of players are
trading.

The recovery story is that **the server is the only authority**. Nothing a player did is stored
on the client, the client's display cache is discarded on disconnect, and the first thing the
market does when it is reopened is ask the server for the current page. So after a crash a player
sees exactly the state that was last saved, with no stale local copy to disagree with it.

## New in this version

### Money ranking
A **Ranking** button in the player market window (left of the backpack) opens a board of every
account on file, richest first. The server rebuilds it at most once every five minutes — the
requirement is a board that refreshes on that interval, and doing it by refreshing a cache rather
than by pushing updates means a hundred players opening the window cause one rebuild rather than a
hundred, and nobody is sent anything they did not ask for. The window states how old the figures
are, because a board that silently shows five-minute-old money is worse than one that admits it.

Ties break on the player's name, so two players holding the same amount cannot swap places between
refreshes — a leaderboard that reorders equal players looks like it is making the numbers up, even
when every figure in it is right. The list covers every account ever created, including players who
have stopped; `/globalshop reload` is not needed for it to update.

### My trades
A **My trades** button opens the viewer's own player-to-player history, newest first. It is
filtered to completed purchases in which they were either the buyer or the seller: listing and
unlisting are not trades, and the server shop keeps a separate log of its own. The request packet
carries **no argument for whose log to show**, so a modified client cannot ask for another player's
history — it is not that the server refuses, it is that the question cannot be asked.

Each line is already phrased from the reader's point of view ("sold 3x Stone to Steve"), and the
amount is labelled as the **gross** transaction amount: the log records what the buyer paid, and
the shop's commission is taken afterwards, so calling that column "income" would overstate what a
seller received.

### The item blacklist (operators)
Operators get a **Blocklist** button that turns the market window into the blacklist editor —
the same window, the same frame, the same backpack, so nothing has to be relearned. Blocked items
appear in the same grid the market uses; drop an item into the slot every other tab already uses
and press **Block item**, or select a blocked item and press **Unblock**.

Blocking is keyed on the item's **registry name**, not on the exact stack. The rule an operator
means is "nobody may sell diamonds"; keying it on the stack would make renaming the diamond the way
around the rule. A renamed or enchanted copy is blocked too, and a refused listing leaves the
items where they were.

The check runs in `ShopService.create`, not in the screen. Hiding the button is a convenience;
a client that skips it is still refused. The list is only ever sent to an operator who asked for
the editor, and the mode is downgraded in the reply for anyone else.

One limit worth knowing: the editor's grid holds one page, 36 items. If more than that are blocked,
the panel says how many are not shown, and removing a visible entry reveals the next.

### /globalshop reload now re-reads the config file
Forge loads a config once, at startup, and does not watch the file — so editing
`config/globalshop-common.toml` on a running server used to do nothing, and vanilla's `/reload` did
not help either because it reloads datapacks rather than configuration. `/globalshop reload` now
reads the file from disk into the live spec before applying it, and says so if no file was found.
There is no need to restart after changing a setting.

Nothing that a player owns lives in the config: it holds policy only, so editing it can never
invent money, items or listings.

## A fix worth calling out: the result message had never been visible

The market window's result banner — "Purchase complete", "Insufficient funds", and now "An operator
has blocked this item from being listed" — was drawn **above** the window, on the reasoning that it
then never fights the layout. That is invisible on any client whose GUI area is exactly the
window's height: the window is 240 tall and so is the scaled area at 854x480, which puts the
window's top edge at y=0 and the banner off-screen at y=-12.

So every result message had silently never been seen. It is now drawn **inside** the window,
across the top of the content area, on a dark backing so it reads over whatever is beneath it, and
**coloured by outcome** — green for accepted, red for refused. A refusal that looks exactly like a
success does not read as a refusal.

A self test now walks every value of `ShopResult` and `ServerShopResult` and asserts that each has
wording in both `en_us` and `zh_cn`, so the next result added cannot quietly show a player a raw
key like `globalshop.result.forbidden_item`.

## The mailbox

### The problem it solves
A purchase was handed to the buyer with `giveOrDrop`. When their backpack was full that dropped the
items on the floor, where a hopper or the five-minute despawn timer destroys them — which a player
cannot tell apart from the shop having eaten their money. Both shops did this: the player market on
a purchase, and the server shop on anything it handed over.

### What happens now
Items go to the backpack first. Whatever does not fit becomes **mail**, and the player is told so in
chat. Nothing is ever dropped on the floor by the shops.

Both shops deliver into **one mailbox**, reached by the **Mailbox** button in the player market
window. A single box rather than one per shop: two places to look for the same thing is a worse
answer to "where did my purchase go?", and every mail is labelled with the shop it came from.

| | |
|---|---|
| Limit | **None** (it takes whatever arrives) |
| Lifetime | `mailLifetimeDays`, default **14** real days, then it is **deleted automatically** |
| Claim | **left-click** a mail |
| Delete | **right-click** a mail |

A "delivery" is one arrival however many stacks it needs — 128 stone is one mail, not two — because
that is what the player experienced.

### The rules that matter

**There is no capacity limit, so a delivery is never refused because the mailbox is full.** Goods go
to the backpack or to the mailbox and never onto the floor — something dropped at a player's feet is
eaten by a hopper or the despawn timer, which is indistinguishable from destroying it.

**Past its lifetime a mail deletes itself.** There is no "expired but still sitting there" state to
clear out by hand: at the end of its life it stops existing, so the box is bounded by time instead
of by count, and a player never has to tidy up a pile of old mail.

**A partial claim does not restart the clock.** If only part of a delivery fits, the remainder stays
as mail with its *original* creation time. Re-stamping it would let a player keep a delivery alive
forever by taking a little at a time, which is exactly what an expiry is there to prevent.

**Claiming takes the mail out before placing the items.** A crash in between leaves the mail in the
world save — nothing is written until the next save, which carries both changes together — rather
than leaving both the mail and a copy of its contents in the backpack, which would be item creation.

### Upgrading
The mailbox used to hold a bare list of stacks with no time on them (returned stock and offline
payouts). Those are read as mail created at the moment of the upgrade: a fresh lifetime is the
generous reading, and discarding a player's goods during an upgrade would not be.

## The private-trade window

### Where it is
A **Trade** button in the free corner at the bottom right of the player market window — under the
listing-slot compartment, which nothing else uses.

### The player directory
Everyone the economy has ever seen, five to a page, with a search box. **Online players are listed
first**, then alphabetically: the people you can talk to right now should not be something you have
to hunt for among a hundred offline names.

A name is **red with "(online)"** when the server said that player is connected, and **grey with
"(offline)"** when it did not. The flag is decided on the server as the page is built, so it is a
fact rather than a guess made from a stale client-side player list.

### Talking
Click a player to open the conversation. The window then shows what the two of you have said —
newest first, your own lines in one colour and theirs in another — with a message box, and the
window's title line still naming your partner and whether they are online.

**Messages to somebody offline are accepted, not refused.** They are kept and the recipient is told
the next time they log in:

> You have 2 new private message(s).
> Read them in the player market: the Private trade button, bottom right.

That second line is the point. A message a player cannot find is the same as a message that was
never delivered, so the notice says where to look. It is announced **once per message**: logging in
five times does not mean being told five times.

### Paying
A payment field and a **Pay** button, paying the player you are talking to directly. It is the same
atomic transfer `/pay` uses, so paying from the window and paying from the command cannot behave
differently. The recipient may be offline — balances are keyed by UUID, so the money is theirs either
way.

The amount travels as **text**, not as a number the client worked out. `MoneyFormat` is configured
from the config file, and a client's config need not match the server's — so the server parses what
the player actually typed, using its own settings.

### Your backpack, as real slots
The window holds the player's own inventory and hotbar as genuine container slots, in the **same
positions** as the market window. A player who has learned where their items are should not have to
find them again. Shift-click moves between the backpack and the hotbar.

### What it deliberately does not do
**There is no item escrow.** What this window provides is a place to agree and a way to pay; the
items change hands the way they always have, by the two players meeting. Inventing an escrow here
would mean inventing a second trade system beside the market's, with its own rules about who may
take what back, and the requirement does not ask for one. If you want a formal two-sided trade with
a confirmation step, that is a separate feature and a bigger one.
