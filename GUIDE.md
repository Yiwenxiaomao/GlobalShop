# GlobalShop Complete Guide

> **English** | [简体中文](GUIDE.zh_cn.md)

> **Minecraft Java Edition 1.20.1 · Forge 47.x**
> One shared economy system for the whole server + player market + official shop + mailbox + private escrow trading + membership.
> No Vault needed, no third-party economy mod needed.

---

## Table of Contents

- [Part 1 · Server Owner](#part-1--server-owner)
  - [1. What It Is](#1-what-it-is)
  - [2. Installation](#2-installation)
  - [3. First-Time Setup in Five Minutes](#3-first-time-setup-in-five-minutes)
  - [4. Official Server Shop](#4-official-server-shop)
  - [5. Item Blacklist](#5-item-blacklist)
  - [6. Economy Management](#6-economy-management)
  - [7. Configuration Hot Reload](#7-configuration-hot-reload)
  - [8. Troubleshooting and Logs](#8-troubleshooting-and-logs)
- [Part 2 · Player](#part-2--player)
  - [9. What the Market Window Looks Like](#9-what-the-market-window-looks-like)
  - [10. Buying Things](#10-buying-things)
  - [11. Selling Things](#11-selling-things)
  - [12. My Listings: Restocking, Repricing, Unlisting, Expiry](#12-my-listings-restocking-repricing-unlisting-expiry)
  - [13. Mailbox: Where Things Go When Your Inventory Is Full](#13-mailbox-where-things-go-when-your-inventory-is-full)
  - [14. Money Ranking and Trade History](#14-money-ranking-and-trade-history)
  - [15. Private Chat and Escrow Trading](#15-private-chat-and-escrow-trading)
  - [16. Membership (VIP)](#16-membership-vip)
- [Part 3 · Reference](#part-3--reference)
  - [17. Complete Command List](#17-complete-command-list)
  - [18. Configuration Quick Reference](#18-configuration-quick-reference)
  - [19. Security Model and Design Trade-offs](#19-security-model-and-design-trade-offs)
  - [20. Known Limitations](#20-known-limitations)
  - [21. FAQ](#21-faq)

---

# Part 1 · Server Owner

## 1. What It Is

| Module | Purpose | Who can use it |
|---|---|---|
| **Economy system** | Currency balances, accounted per player UUID, independent of online status | Everyone |
| **Player market** | Players buy and sell among themselves; the server only handles escrow and bookkeeping | Everyone |
| **Official server shop** | The owner sets prices to sell / buy back items in unlimited quantity | Everyone (admin page is OP only) |
| **Mailbox** | Where items land when the inventory can't hold them; offline refunds go through it too | Everyone |
| **Private trading** | Player list, private chat, offline messages, **two-way escrow trading** | Everyone |
| **Item blacklist** | The owner forbids certain items from being listed | OP only |
| **Membership** | Bought per period, raises the simultaneous listing limit, can auto-renew | Everyone |

**The one-line security principle**: every operation involving money and items is **executed only on the server side**, and the validation and the change happen **inside the same lock**. The client only sends "what I want to do", never a price, a quantity result or a balance. So a modified client cannot conjure money or items out of thin air; at most it can request operations that will be refused.

---

## 2. Installation

1. **Both the server and the client need it.** This is a dual-side mod; mismatched protocol versions will **refuse the connection outright**, rather than mis-parse packets
2. Put `globalshop-1.0.0.jar` into the `mods/` folder
3. Start the server once and it will generate `globalshop-common.toml` in `config/`

**Dependencies**: only Forge 47.x and Java 17. No Vault, no other economy mod.

---

## 3. First-Time Setup in Five Minutes

### 3.1 Confirm the currency units (**the item people get wrong most often**)

```toml
minorUnitsPerMajor = 100
currencyName = "Gold"
```

It means "**100 minor units = 1 unit of currency**". So every amount written in the config is in **minor units**:

| What the config says | What the player sees |
|---|---|
| `100` | `1.00` |
| `10000` | `100.00` |
| `1000000` | `10000.00` |

> Want "1 dollar" to equal 1 minor unit? Set `minorUnitsPerMajor` to `1`.
> **But note: balances already recorded in the save are not converted**, so changing this value changes every player's money proportionally.

### 3.2 Decide how players get their first money

```toml
initialBalance = 0     # by default new players have no money
```

If you want new players to start with seed money, change this (same units as above).

### 3.3 Check that these two match your expectations

```toml
marketCommissionPercent = 5    # every market trade takes a 5% commission
listingLifetimeHours = 12      # listings expire automatically after 12 hours
```

| Desired effect | How to set it |
|---|---|
| Turn off commission | `marketCommissionPercent = 0` |
| Never expire | `listingLifetimeHours = 0` |
| Forbid players from listing (official-shop-only mode) | `allowListing = false` |

**The commission is deducted from the total trade amount and destroyed**, it does not go into any account — it is a currency sink, not a third account.

### 3.4 Give administrator permissions

All administrative commands require **permission level 2** (normal OP). Just give the player OP.

### 3.5 Done

Go into the game and type `/globalshop help` to see all the commands. Craft a **market terminal** block and put it at spawn, or write `/globalshop market` on a sign.

---

## 4. Official Server Shop

### 4.1 Opening and configuring

```
/globalshop server                                      # open the shop
/globalshop server add <item> <price> <buyback price> <purchase amount at a time> [purchase limit]
/globalshop server remove <item>
/globalshop server list
/globalshop server logs [count]
/globalshop server reload                               # re-sync the built-in goods
/globalshop server resetDaily <player>                    # reset someone's buyback quota for today
```

### 4.2 Three pages

| Page | Content |
|---|---|
| **Sell** | Goods listed by the owner; players pay money to buy them (a per-player purchase limit can be set) |
| **Buyback** | Players sell items to the server for money (**daily quota**) |
| **Admin** | OP only: list / reprice / unlist |

### 4.3 The iron rule of pricing: the buyback price must be lower than the sale price

Otherwise players can "buy it and sell it straight back" to farm money endlessly. The mod **refuses such pricing on the server side**, and says so right in the interface.

> But a "**buyback-only**" entry with a **sale price of 0 and only a buyback price** is **allowed** — that is "the server buys this item", not "the server sells this item".

### 4.4 How the daily buyback quota is calculated

```toml
dailyRecycleLimit = 128     # how many items each player can sell back per day
timezone = ""               # which timezone the daily reset uses, empty = server system timezone
useOnlineTime = false       # true = only count server uptime
```

- **By default it resets on the real date** (in the timezone given by `timezone`)
- With `useOnlineTime = true` it instead counts **server uptime**, preventing "shut the server down to get past the date" quota farming
- Write timezones as names like `Asia/Shanghai`

### 4.5 Terminal blocks

```toml
sellMarketTerminal = true        # whether to sell the market terminal block
marketTerminalPrice = 100000     # market terminal price
marketTerminalPurchaseLimit = 10 # purchase limit
stockOwnTerminal = false         # whether to also sell the server shop terminal
serverShopTerminalPrice = 5000
serverShopTerminalPurchaseLimit = 0   # 0 = no limit
```

---

## 5. Item Blacklist

```
/globalshop block <item>      # forbid this item from being listed
/globalshop unblock <item>    # lift the ban
/globalshop blocklist         # view the list (OP only)
```

Or click the **Blacklist** button to the left of the inventory in the market window to open the editor:

- Put the item into the slot → click "Forbid listing"
- Click a forbidden item → click "Lift the ban"

### Key design: intercept by **item**, not by that stack

The basis for interception is the item's **registry name**. The rule is "nobody may sell diamonds"; if it intercepted that specific stack, **renaming it or putting an enchantment on it would become a way around the rule**. So renamed copies are blocked just the same.

A refused listing **does not take your item away**. The interception runs **on the server side**, not merely in the interface — a modified client is refused just the same.

> **Limitation**: the editor grid has only one page of **36 entries**. When there are more, the panel states how many are not shown, and removing one reveals the next.

---

## 6. Economy Management

```
/money                            # view your own balance
/money give <player> <amount>          # OP
/money take <player> <amount>          # OP
/money set  <player> <amount>          # OP
/money <player>                     # view someone else's balance (OP)
/pay <player> <amount>                 # peer-to-peer payment
/globalshop money ...             # synonym forms of all of the above (to avoid command conflicts)
/globalshop pay <player> <amount>
```

- `/pay` and the payment inside private trading use **the same atomic transfer**, so behaviour does not differ by entry point
- The balance cap is controlled by `maxBalance` (to prevent overflow)

---

## 7. Configuration Hot Reload

```
/globalshop reload
```

**No server restart needed.**

Forge only reads the configuration once at startup, and **does not watch for file changes**; vanilla `/reload` reloads datapacks, not configuration. So this command **actively re-reads the config file from disk** and then applies it — the currency format, the various caps and the log retention count all update along with it.

If it cannot find the config file, it will tell you it "re-applied from memory".

> **Important**: Forge **keeps the old values already present in the config file**. If a mod update changes the default of some key and your config file already has that key, **the new default will not take effect**. That is why this project **also renames the key** whenever a default has to change.

---

## 8. Troubleshooting and Logs

```
/globalshop logs [count]    # trade log (listing / purchase / unlisting / admin actions)
/globalshop ids [count]     # ids of the items currently listed
/globalshop remove <id>    # force-unlist an item (the item returns to the seller)
/globalshop accounts       # list all accounts with their UUID and balance, filterable by name
```

In the server log (`logs/latest.log`), **refused operations** are recorded with the `globalshop:` prefix:

```
globalshop: INSUFFICIENT_FUNDS refused for Steve (query=...)
globalshop: mailbox full for Alex, dropping 1 stack(s) instead
```

**The second line should never appear under normal circumstances** — it means "both the inventory and the mailbox are full, but the operation had already completed". If you see it, please send me the log.

---

# Part 2 · Player

## 9. What the Market Window Looks Like

```
┌──────────────────────────────────────────────────────────┐
│ [Search box]          [Price][Time][Seller]   VIP Balance 3110.50 │ ← click VIP for membership
├──────────────────────────────────────────────────────────┤
│ [Market] [My Listings] [Sell]          < 1/1 >      [Refresh] │
├───────────────────────┬──────────────────────────────────┤
│                       │  Item name / enchants / durability │
│    Item grid 6x6      │  Seller name + badge               │
│    (click to select)  │  Unit price / stock / listed at    │
│                       │  [- 1 +]  Total                    │
│                       │  [        Buy        ]             │
├───────────────────────┼──────────────────────────────────┤
│ [Ranking]  ▓▓ Backpack 36 slots │  [Listing slot]         │
│ [Trade history]       │  [Private trading]                 │
│ [Blacklist]           │                                    │
│ [Mailbox]             │                                    │
└───────────────────────┴──────────────────────────────────┘
```

---

## 10. Buying Things

1. Click an item in the grid on the left
2. The right side shows the details: **item name, enchantment list, durability, seller name and identity, unit price, remaining stock, list time**
3. Use `-` / the input box / `+` to choose a quantity; the **total price for this order** is shown live
4. Click **Buy**

**Settlement rules**:

| Situation | Result |
|---|---|
| It fits in the inventory | Goes straight into the inventory, **success sound** |
| Inventory full but the mailbox has room | **Goes into the mailbox**, the chat bar tells you |
| Both inventory and mailbox full | **The purchase is refused in advance, not a cent is deducted** |
| Not enough money | Refused, **failure sound** |

When items **cannot be placed, they are not dropped on the ground** (dropped items would be sucked up by hoppers or despawn).

---

## 11. Selling Things

1. Put the item into the **listing slot at the bottom right**
2. Switch to the **List** tab
3. Fill in the price, the stock, and the minimum / maximum purchase amount per transaction
4. Click **List**

The item **is escrowed** (it leaves your inventory) until it sells or you unlist it.

**What happens when you list**:

- Reaching the listing count cap is refused (normal `listingLimit`, member `vipListingLimit`)
- An item on the blacklist is refused (**the item is not taken away**)
- A price above `maxUnitPrice` is refused
- The shop takes a `marketCommissionPercent` commission; **the system message at sale time states the after-tax amount**

**Command form** (uses the item in your main hand, no need to open a window):

```
/globalshop sell <price> <count>                 # min 1, max 64
/globalshop sell <price> <count> <min> <max>
```

---

## 12. My Listings: Restocking, Repricing, Unlisting, Expiry

The **My Listings** tab shows all your listed items, with four buttons at the bottom:

| Button | Purpose |
|---|---|
| **Restock** | Add **all** of that item from your inventory into this listing **in one go** (the count is tallied by the server) |
| **Save** | Save your changes to the price / stock / purchase limit |
| **Unlist** | Unlist the selected listing; the escrowed item returns to the inventory (or to the mailbox if the inventory is full) |
| **Unlist all** | Unlist **all** your listings |

### Expiry

After a listing has been up longer than `listingLifetimeHours` (default **12 hours of real time**):

- It **disappears from the public market**; nobody can search for it or buy it
- In "My Listings" it **dims and is marked as expired**
- **Only the seller can unlist and take it back**; the item is always yours

> Expiry is computed **by the real clock**, so **it keeps running even while the server is off**.

**Command form**:

```
/globalshop mine                                   # list your listed items (with the id each one needs)
/globalshop buy <id> [count]                        # buy
/globalshop unlist <id>                            # unlist one
/globalshop unlistall                              # unlist all at once
/globalshop restock <id>                           # restock
/globalshop modify <id> <stock>                      # change the stock only
/globalshop modify <id> <stock> <price> <min> <max>   # change price + stock + purchase limit
```

---

## 13. Mailbox: Where Things Go When Your Inventory Is Full

**Entry point**: the **Mailbox** button to the **left of the inventory** in the market window.

| | |
|---|---|
| Cap | `mailboxLimit`, default **100 pieces** |
| Validity | `mailLifetimeDays`, default **14 days** (real time) |
| **Left click** | Claim |
| **Right click** | Delete |

**The bottom of the interface always reads "Left click: claim　Right click: delete"** — no guessing required.

A few rules that matter:

- **One mail = one delivery**, no matter how many slots it needs (128 cobblestone is 1 mail, not 2)
- **Expired mail cannot be claimed, only deleted**, and is shown greyed out
- **Expired mail does not count against the cap** — when new mail arrives it automatically clears expired mail to make room, so you will never be permanently blocked by a box of old mail
- **Partial claims do not reset the timer**: if only part of it fits into the inventory, the rest stays with its **original creation time**; claiming half does not refresh the 14 days
- **It is refused before money is deducted**: if the items fit neither in the inventory nor in the mailbox, the purchase is **refused in advance**

**Mail sources**: inventory full at purchase time, expiry-driven unlisting, admin force-unlisting, and the other party's full inventory during private trading.

Command form (**indices start at 1**, no need to copy long ids):

```
/globalshop mail              # list the mailbox
/globalshop mail claim 3      # claim the 3rd one
/globalshop mail delete 3     # delete the 3rd one
/globalshop mail claimall     # claim as many as possible (whatever doesn't fit stays)
/globalshop claim             # shorthand for claimall
```

---

## 14. Money Ranking and Trade History

### Money ranking

**Entry point**: the **Ranking** button to the left of the inventory in the market window.

- Shows **every player who has ever had an account created** on the server, balances from high to low, 12 per page, the top three in gold
- **Updates once every 5 minutes** (server-side cache); the top of the window states "updated N minutes ago"
- Ties are sorted by name, so **positions do not swap on every refresh**

Command: `/globalshop rank [page]` (10 per page)

### Trade history

**Entry point**: the **Trade history** button to the left of the inventory in the market window.

- Shows only player-to-player trades that **you yourself** took part in, newest first
- Sales are a green `+`, purchases a red `-`
- The amount column is labelled "**Trade amount (pre-tax)**": the log records how much the buyer paid; the commission is deducted afterwards

**The request packet has no "whose records to view" parameter**, so a modified client **cannot ask for** anyone else's records either.

Command: `/globalshop trades [count]`

---

## 15. Private Chat and Escrow Trading

**Entry point**: the **Private trading** button at the **bottom right** of the market window (below the listing slot).

### 15.1 Player list

- **5 per page**, pageable, searchable by name with the search box
- **Online = red + (Online)**, **offline = grey + (Offline)**
- **Online players are sorted first**, then by name
- Online status is decided by **the server when it builds the page**, not guessed by the client

### 15.2 Chat and offline messages

Click a player to enter the conversation (the left half of the window), type a message and press enter to send.

**Messages sent to offline players are saved**, and the next time they log in they will see this in the chat bar:

> You have 2 new private messages.
> Where to view them: Player Market → the "Private trading" button at the bottom right.

**The notice states where to look** — a message you cannot find is a message that was never delivered.

Commands:

```
/globalshop pm <player> <content>    # send
/globalshop pm <player>           # view the most recent conversation
```

### 15.3 Escrow trading (the right half of the window)

```
┌────────────────────────────┐┌──────────────────────┐
│ Private chat                    ││ Offer                  │
│                            ││ [Item slot] Diamond x3       │
│                            ││ You are selling / You are buying     │
│                            ││ Price 800.00            │
│                            ││ [Price input box]           │
│ [type a message, press enter]   [Send]  ││ [Change price] [Pay 800.00]│
│                            ││ Priced: pay to close the deal     │
│ Inventory / hotbar (real slots)    │└──────────────────────┘
└────────────────────────────┘
```

**The complete flow**:

| Step | Who | Does what |
|---|---|---|
| 1 | Seller | Put the item into the slot → click "**Offer**" → the item **is escrowed**, visible to both sides |
| 2 | Seller | Enter a price → click "**Set price**" → **both sides see that price immediately** |
| 3 | Either side | Click "**Change price**" (the price box unlocks) → enter the new price → click "**Confirm price**" → both sides see the new price |
| 4 | Buyer | Click "**Pay 800.00**" → the money goes to the seller, **the item goes to the buyer** (or to the mailbox if the inventory is full) |
| 5 | Seller | Click "**Withdraw**" → the item returns (or goes to the mailbox if the inventory is full) |

**The buyer has nowhere to enter an amount** — the number on the pay button is **the negotiated price**.

**The price box is locked by default**; changing the price requires the two steps "change → confirm", to prevent an accidental click from changing a price that has already been agreed.

#### A seller leaving refunds the goods

> **Important invariant: an offer exists only while the seller's trade window is open.**

Closing the window (or disconnecting, or logging back in after a server restart) **automatically returns** the item (or sends it to the mailbox if the inventory is full). So an item is never left sitting somewhere you cannot see.

Why it is designed this way: an offer escrows **a real item**. If it stayed after the window closed, the seller would have something that "cannot be seen anywhere" — from the player's point of view, the item vanished. Closing the window is a clear enough "I don't want this for now", and returning it is the reading that loses nothing.

**The cost**: you cannot "post it and walk away, waiting for the other side to buy later". The two models cannot both hold.

#### Guarding against "repricing at the last second"

When the buyer clicks pay, the client sends along **the price it saw**. If the server finds the price has been changed, it **refuses the payment**:

```
PASS  paying a price that has moved is refused
PASS  and it took no money
```

Otherwise the seller could change the price an instant before you click, and you would pay the new price.

#### Command form

```
/globalshop trade                        # open the trade window
/globalshop trade offer <player> <price>     # offer the main-hand item to that player at that price
/globalshop trade price <player> <amount>     # change an already-published price
/globalshop trade info <player>            # view the current offer
/globalshop trade pay <player>             # pay at the published price
/globalshop trade withdraw <player>        # withdraw
```

---

## 16. Membership (VIP)

**Entry point**: the identity badge **to the left of the balance** at the top of the market window (grey = normal / purple = member) — **it is a button; one click opens the membership screen**, and hovering over it highlights it.

In the membership screen you can see:

- Your current identity and **remaining days**
- **The price and the duration**
- The **Subscribe / Renew** button
- The **auto-renew toggle**, along with a clear statement of "how much will be deducted automatically at expiry" or "it will simply lapse at expiry"

| Identity | Simultaneous listing limit |
|---|---|
| Normal | `listingLimit`, default **10** |
| Member | `vipListingLimit`, default **20** |

### Membership is bought by **period**, not permanently

Default **14 real days** (`vipDays`).

### Renewal **stacks**, it does not overwrite

Buying again while still within the validity period **adds one more period** on top of the existing expiry time — it does not wipe out the time you have already paid for.

### Auto-renew

| Situation | Behaviour |
|---|---|
| Expired + auto-renew on + enough balance | **Charges automatically, extends by one more period**, and notifies you |
| Expired + not enough balance | **No charge, no debt**, auto-renew **turns itself off**, and it **tells you on the spot how much you are short** |
| When it checks | **On login** + **every 5 minutes** while the server is running (staying online will not silently drop your rank) |

> Silent lapse is worse than the lapse itself, so the mod chooses to say so plainly rather than quietly downgrade you.

### Commands

```
/globalshop vip          # subscribe / renew
/globalshop vip info     # view identity, remaining days, price, auto-renew status
```

---

# Part 3 · Reference

## 17. Complete Command List

### Player commands

| Command | Purpose |
|---|---|
| `/shop` · `/globalshop` · `/globalshop open` · `/globalshop market` | Open the player market |
| `/globalshop help` | **List all commands** |
| `/globalshop sell <price> <count> [min] [max]` | List the main-hand item |
| `/globalshop buy <id> [count]` | Buy an item |
| `/globalshop mine` | Your listed items (with ids) |
| `/globalshop unlist <id>` | Unlist one |
| `/globalshop unlistall` | Unlist all at once |
| `/globalshop restock <id>` | Restock with the same kind of item from your inventory |
| `/globalshop modify <id> <stock> [price] [min] [max]` | Change price / stock / purchase limit |
| `/globalshop mail [list]` | List the mailbox |
| `/globalshop mail claim <index>` | Claim the Nth one |
| `/globalshop mail delete <index>` | Delete the Nth one |
| `/globalshop mail claimall` · `/globalshop claim` | Claim as many as possible |
| `/globalshop rank [page]` | Money ranking |
| `/globalshop trades [count]` | Your own trade history |
| `/globalshop pm <player> [content]` | Send a private message / view the conversation |
| `/globalshop trade` | Open the private trading window |
| `/globalshop trade offer <player> <price>` | Offer the held item |
| `/globalshop trade price <player> <amount>` | Change the price |
| `/globalshop trade info <player>` | View the offer |
| `/globalshop trade pay <player>` | Pay at the published price |
| `/globalshop trade withdraw <player>` | Withdraw the offer |
| `/globalshop vip` | Subscribe / renew membership |
| `/globalshop vip info` | View membership status |
| `/globalshop server` · `/globalshop server open` | Open the official shop |
| `/money` · `/pay <player> <amount>` | Balance and payment |

### Administrator commands (permission level 2)

| Command | Purpose |
|---|---|
| `/globalshop server add\|remove\|list\|logs\|reload\|resetDaily` | Official shop management |
| `/globalshop block <item>` | Forbid listing that item |
| `/globalshop unblock <item>` | Lift the ban |
| `/globalshop blocklist` | View the blacklist |
| `/globalshop vip grant\|revoke <player>` | Grant / revoke membership |
| `/globalshop reload` | Re-read the config file (no restart) |
| `/globalshop logs [count]` | Trade log |
| `/globalshop ids [count]` | Ids of listed items |
| `/globalshop remove <id>` | Force-unlist an item |
| `/globalshop accounts [name]` | List all accounts (name / UUID / balance), filterable by name |
| `/money give\|take\|set <player> <amount>` | Give money / take money / set balance |

---

## 18. Configuration Quick Reference

`config/globalshop-common.toml`

### Economy

| Key | Default | Description |
|---|---|---|
| `initialBalance` | `0` | Initial balance for new players |
| `maxBalance` | `1000000000000` | Balance cap (to prevent overflow) |
| `minorUnitsPerMajor` | `100` | Minor unit conversion ratio |
| `currencyName` | `Gold` | Currency name |

### Market

| Key | Default | Description |
|---|---|---|
| `allowListing` | `true` | Whether players may list items |
| `listingLimit` | `10` | Simultaneous listing cap for normal players |
| `vipListingLimit` | `20` | Simultaneous listing cap for members |
| `vipPrice` | `1000000` | Membership price (**per period**) |
| `vipDays` | `14` | Length of one membership period (real days) |
| `marketCommissionPercent` | `5` | Trade commission percentage, **0 = off** |
| `listingLifetimeHours` | `12` | Listing validity (real hours), **0 = never expires** |
| `maxUnitPrice` | `1000000000` | Unit price cap |
| `maxStock` | `1000000` | Stock cap |
| `maxPerPurchase` | `4096` | Maximum quantity per purchase |
| `purchaseCooldownMs` | `150` | Anti-spam interval between actions (also used for private chat / trading) |
| `tradeLogRetention` | `2000` | Number of log entries retained (also used for private chat records) |

### Mailbox

| Key | Default | Description |
|---|---|---|
| `mailboxLimit` | `100` | Mailbox cap (pieces) |
| `mailLifetimeDays` | `14` | Mail validity (real days) |

### Official shop

| Key | Default | Description |
|---|---|---|
| `dailyRecycleLimit` | `128` | Buyback quantity per player per day |
| `timezone` | `""` | Timezone for the daily reset, empty = system timezone |
| `useOnlineTime` | `false` | Count server uptime only |
| `ntpHost` | `pool.ntp.org` | Time synchronisation server |
| `ntpTimeoutMs` | `3000` | Time synchronisation timeout |
| `sellMarketTerminal` | `true` | Whether to sell the market terminal block |
| `marketTerminalPrice` | `100000` | Market terminal price |
| `marketTerminalPurchaseLimit` | `10` | Market terminal purchase limit |
| `stockOwnTerminal` | `false` | Whether to also sell the server shop terminal |
| `serverShopTerminalPrice` | `5000` | Server shop terminal price |
| `serverShopTerminalPurchaseLimit` | `0` | Its purchase limit (0 = unlimited) |

> **After changing anything run `/globalshop reload`; no restart needed.**

---

## 19. Security Model and Design Trade-offs

### 19.1 Authority lives only on the server

- The client **does not send** prices, quantity results or balances — only "what I want to do"
- Every packet handler **re-validates**: the corresponding window must be open, the item must really be in the inventory / slot, the price must conform to the configuration
- Amounts are transmitted as **text** and parsed by the server using **its own configuration**. That way a client that changed its currency settings cannot affect the amount the server computes

### 19.2 Atomicity of money and items

- Packaging operations complete **inside the same lock**
- **The buyer's full amount is deducted first, then the seller is credited** — the other way round, if deducting the commission failed (for example the buyer spent the money through `/pay` at the same time), there would be a hole where "the seller already received the money, yet the purchase reports failure"
- Item escrow uses "**validate first, then take**": if it does not fit, it is simply not taken

### 19.3 The commission is a currency sink

The commission on market trades **is destroyed**; it does not go into any account. Private trading **charges no commission** (it is equivalent to `/pay`; charging one would make the same transfer cost different amounts depending on the entry point).

### 19.4 The trade-off of per-period membership

Per period plus auto-renew, rather than a one-off permanent buyout. The advantage is that the price can be adjusted with the economic environment; `/globalshop vip info` and the interface both state the remaining time clearly, so nobody is led to believe they bought it outright.

---

## 20. Known Limitations

1. **Private-chat offers are "session-level"** — closing the window returns the goods; **you cannot "post it and walk away, waiting for the other side to buy later"**. This is a deliberate trade-off (see 15.3)
2. **A crash may lose the last one or two trades** — a trade either takes effect as a whole or not at all. But completed trades are flushed to disk with the world, so **force-killing the process within a few seconds** after a trade **loses the entire trade**
3. **The ranking includes every historical account**, including players who stopped playing long ago
4. **The blacklist editor has only one page (36 entries)**
5. **The interface is designed within 320×240** — the size that guarantees nothing is cut off at 854×480 (427×240 after scaling); the window will not grow any larger
6. **There is only one shared mailbox** — the player market and the official shop share it; mail states its source

---

## 21. FAQ

**Q: A player's inventory was full when buying; where did the items go?**
Into the **mailbox** (the mailbox button on the left of the market window). If **both the inventory and the mailbox are full**, the purchase is **refused in advance**, and no money is deducted.

**Q: A listed item disappeared?**
Past `listingLifetimeHours` it **expires**: it vanishes from the public market but stays in your "My Listings" (dimmed). Unlist it to get it back.

**Q: Why does the seller receive less than the buyer paid?**
The `marketCommissionPercent` commission, 5% by default, is deducted from the total and **destroyed**. The system message at sale time states the **after-tax amount**.

**Q: Why did my config change not take effect?**
Run `/globalshop reload`. Also check that you changed **the right key name** — an old config may still contain a renamed old key, and **only the new set takes effect**.

**Q: Why does listing say "this item has been forbidden from listing by the server owner"?**
It is on the blacklist. An OP can view it with `/globalshop blocklist`.

**Q: Why can't the buyback price equal the sale price?**
Otherwise you could "buy it in and sell it back" to farm money endlessly. But a **buyback-only entry** with a sale price of 0 and only a buyback price is allowed.

**Q: How do I let players transfer money to each other?**
`/pay <player> <amount>`, or pay through an offer in the private trading window (recommended, because the price is **negotiated** rather than hand-typed).

**Q: What happens when membership expires?**
The listing cap drops back to the normal value. **Listings already up are not unlisted**, but while you are above the cap you cannot list anything new until you come back down.

**Q: Can membership be refunded?**
No. But while auto-renew is on you can turn it off at any time; it then simply lapses at expiry and no further charges are made.

**Q: Can I turn a feature off?**
- Commission: `marketCommissionPercent = 0`
- Listing expiry: `listingLifetimeHours = 0`
- Forbid player listings: `allowListing = false`
- Don't sell terminal blocks: `sellMarketTerminal = false`

---

## Appendix: The Server Owner's Daily Checklist

```
/globalshop help                  # look here when you can't remember all the commands
/globalshop server list           # what the official shop has listed
/globalshop server logs 20        # recent shop trades
/globalshop logs 20               # recent market trades
/globalshop ids 20                # currently listed items, for use with remove
/globalshop blocklist             # the blacklist
/globalshop reload                # after changing the config
```

---

*This guide corresponds to GlobalShop 1.0.0 (Forge 1.20.1-47.x).*
