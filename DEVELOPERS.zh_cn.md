# GlobalShop 开发说明

写给要往这个模组里加功能、或者要写配套模组的人。**请在读代码之前先读这份**，因为代码遵循一些
从任何单个文件里都看不出来的规矩。

---

## 1. 源码的形状

```
com.example.globalshop
├── GlobalShop              @Mod 入口
├── Config                  所有配置项集中在一个文件，每项一个静态 getter
├── api/                    受支持的接口。附属模组只编译这里，别的都不碰
│   ├── GlobalShopApi       入口：经济、位置、版本
│   ├── economy/            钱：接口、定位器、结果、格式化
│   ├── feature/            第二页三十二个位置、它们的网络包、以及客户端的副本
│   └── event/              模组自己的事件
├── core/                   1.0.0 的本体
│   ├── market/             固定价市场：服务、存档、结果、网络包、界面
│   ├── social/             邮箱、黑名单、私聊、交易请求
│   ├── servershop/         服务器自营商城、它的时钟和标签页
│   ├── vip/                VIP 界面与它的网络包
│   └── menu/  registry/  command/  event/  network/  client/
├── feature/                1.1.0 每个功能一个包，各自拥有它需要的全部文件
│   ├── auction/            服务 · 存档 · 结果 · 上架 · 观众 · 事件 · net/ · client/
│   ├── bank/  checkin/  pvp/  teleport/  title/   同样的形状
│   └── ranking/  tradelog/                        还有这两个
├── internal/               不属于承诺范围的实现
│   └── economy/            内置经济、它的存档和它的网络包
└── test/                   SelfTest，整个自测套件写在一个类里
```

**规矩只有一句：一个功能独占自己的文件。** 关于拍卖的一切 —— 服务、存档、结果枚举、网络包、界面 ——
都在 `feature/auction/` 里。想给银行加第五样东西，只需要打开 `feature/bank/`，不用看别处。
在这次整理之前，这些东西散落在三个互不相干的目录里，这一章就是为那件事写的。

`api/` 是这条规矩的另一半，也是唯一带承诺的部分：附属模组编译它。它的包文档写明了什么受支持。
它外面的一切都可能在任何版本改变。**第 6 节**是写给附属模组作者的。
---

## 2. 每个功能都遵循的套路

写新功能之前，**把一个功能从从头到尾读一遍**。`bank/` 是最小的完整例子。

### 五个文件

| 文件 | 是什么 | 照抄哪个 |
|---|---|---|
| `XSavedData` | 存档。继承 `SavedData`，每服务器一份，存进世界。 | `BankSavedData` |
| `XService` | 所有规则。**除它之外没有任何地方做决定。** | `BankService` |
| `XServices` | 发放每服务器单例，并在关服时释放。 | `BankServices` |
| `XResult` | 一个结果枚举，每项带一个翻译键。 | `BankResult` |
| `XActionPacket` / `XDataPacket` | 网络会话的两个方向。 | `BankActionPacket`、`BankDataPacket` |

另外还有该功能自己 `client/` 包里的一个界面，以及 `core/client/ClientScreens` 里的一行。

### 这些文件遵守的规矩

* **先校验、再修改，全程在 `synchronized (data)` 里。** 一个"读余额 → 判断 → 写余额"的服务方法
  就是竞态。每一个都长这样：

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

* **绝不复用别的功能的 `Result` 枚举。** 每个功能自己一个，这样给某个功能加一种结果，
  **不会**改变另一个功能里某个值的含义。

* **界面不做决定。** 界面发一个动作，服务端重新校验一切，再回一个数据包。
  一个在本地就拒绝的界面，早晚有一天会和服务器说法不一致。

* **不信任客户端来的任何东西。** 金额在服务端用服务端自己的货币设置解析，并再次对照钱包、
  存款或上限校验。

* **玩家能读到的一切都有翻译键**，`en_us.json` 和 `zh_cn.json` **两边都要有，键必须一致**。
  `tools/check_i18n.py` 会在不一致时报错。

---

## 3. 具体怎么做

### 往左列加一个功能

`core/menu/ShopLayout.java` 里一个槽位一个常量（`BANK_BUTTON_Y` 之类）。加一个常量，在
`ShopScreen.init` 里创建按钮，加进 `columnPageOne` 列表，并在 `refreshWidgets` 里设置它的可见性。
三个标签页和翻页按钮的可见性**只在那里**决定，别的地方不要动。

### 用第二页的一个位置

第二页有**三十二个位置**，在 `ShopScreen.buildPageTwoCells` 里创建，目前什么都不做。
给其中一个接上动作，它就是一个功能了 —— 按钮、悬停、点击、槽位隐藏**都已经做好了**。
在按钮的 lambda 里发一个动作包，然后回一个数据包即可。

### 碰钱

钱只走 `internal/economy/InternalEconomyService`。它有一个重要性质：
**每一次成功的变动都会主动通知客户端。** 调用 `setBalance`、`addBalance`、`removeBalance`
或 `transfer`，玩家屏幕上的数字**会自己更新**。不要直接写余额，也不要自己发余额包 ——
刷新按钮是历史原因留下的，它不属于任何正常工作流程。

### 加一个配置项

`Config.java` 里放一个 `ForgeConfigSpec` 字段、一个带 null 兜底的静态 getter、以及一段说明
**为什么默认值是它**的注释。然后到 `GUIDE.md` **和** `GUIDE.zh_cn.md` 的表格里各加一行 ——
`tools/check_docs_against_code.py` 会拿它和代码比对，漏了会失败。

### 加一条命令

`core/command/ShopCommand.java`，作为 `/globalshop` 的子命令。命令字面量同样要出现在指南里，
因为同一个检查工具会查它。

### 加一个网络包

在 `core/network/ModNetwork.java` 里每个功能注册两个：动作包和数据包。**必须加在最后。**
消息 id 是按注册顺序用计数器分配的 —— 在中间插入会让它后面所有的 id 全部改变，
于是用不同版本编译的客户端和服务端会对应错位。

---

## 4. 会咬人的地方

| 不能动的东西 | 为什么 |
|---|---|
| **`SavedData` 的名字是永久的。** | 传给 `computeIfAbsent` 的字符串就是世界存档里的**文件名**。改掉它不会迁移任何数据，只会静默地从空开始，而旧文件被丢在那里。 |
| **网络包 id 是按顺序分配的。** | 见上。**只能往后加，不能中间插。** |
| **改动任何网络包的形状，都要升 `PROTOCOL_VERSION`。** | Forge 会在两端校验这个字符串，它是唯一能把"客户端和服务端版本不一致"变成**登录时就被拒绝**的东西。不升的话两边会顺利连上，然后在之后某个时刻爆发成"客户端读过了包的末尾"—— 玩家被踢下线，而服务端日志里什么都没有。 |
| **钱会自己通知客户端。** | 见上。绕过经济服务去动钱的功能，出问题的样子会非常难查。 |
| **银行先加锁，经济后加锁，绝不能反过来。** | 全项目只有这一个加锁顺序，这正是死锁不可能发生的原因。请保持。 |
| **槽位隐藏靠的是 `Slot.isActive()`。** | 容器会画它拥有的每一个槽位并查询 `isActive`，没有第二个开关。**涂色盖住槽位是没用的** —— 物品是容器在你涂色之后画的。 |
| **第二页的遮罩画在 `renderBg` 里。** | 要在容器画控件**之前**，这样按钮才会落在遮罩之上。画晚了就会把按钮盖掉 —— 这正是当初花了好几轮才找到的那个 bug。 |
| **布局是被审计的。** | `tools/make_gui_preview.py` 从 Java 源码里读坐标，报告重叠和越界。**坐标写成内联字面量的窗口无法被审计**，所以请放进有名字的常量。 |
| **永远不要改已有的翻译键。** | 玩家的设置、其他模组的联动、文档都在引用它们。要加就加新的。 |

---

## 5. 收工之前

```bash
python tools/check_i18n.py             # 没有硬编码，两种语言都完整
python tools/check_docs_against_code.py # 每个配置键和命令都有文档
python tools/check_guide_parity.py      # GUIDE.md 与 GUIDE.zh_cn.md 仍然对应
python tools/make_gui_preview.py        # 没有重叠，没有越界
..\_toolchain\gsbuild.cmd runServer --console=plain --no-daemon --offline   # 自测套件
..\_toolchain\gsbuild.cmd build --console=plain --no-daemon --offline
```

**自测套件就是契约：645 项检查，必须保持零失败。** 它跑在一个真实的服务器上，所以能抓到
单元测试抓不到的东西 —— 吞掉物品的托管、让玩家负债的决斗、不刷新的余额。加功能就加检查；
**检查失败时，通常检查是对的、代码是错的**。到目前为止每一次都是这样，包括那些不方便的时候。

套件里有两个习惯值得照抄：它会**清空并还原**它用到的玩家，因为套件会连着跑两次而存档是持久的；
它在**锚点找不到时会大声失败**而不是跳过，因为一个静默不跑的检查比没有检查更糟。

---

## 6. 写给附属模组作者：受支持的接口

`com.example.globalshop.api` 下面的一切都是**承诺**。它外面的一切都是实现细节，任何版本都可能改 ——
这正是这层拆分存在的理由：没有它，附属模组的作者只能在一百五十七个类里猜哪些是安全的。

### 里面有什么

| 类 | 用途 |
|---|---|
| `api.GlobalShopApi` | 入口。从这里开始，启动阶段你不需要知道别的类名。 |
| `api.economy.EconomyService` | 钱。和模组自己的市场、银行、拍卖**用的是同一个实例**。 |
| `api.economy.EconomyServices` | 取到那个实例，或者用你自己的实现替换它。 |
| `api.economy.TransactionResult` | 一次金钱操作的结果，包括它拒绝的原因。 |
| `api.feature.FeaturePlace` | 第二页三十二个位置中的一个。 |
| `api.feature.FeaturePlaces` | 注册一个位置，以及查看某个下标是否已被占用。 |
| `api.event.BalanceChangedEvent` | 任何来源的余额变动之后触发。 |
| `api.event.FeaturePlaceUsedEvent` | 位置被执行之前触发，可取消。 |

### 收钱和付钱

```java
EconomyService economy = GlobalShopApi.economy(server);
if (economy.hasEnough(player.getUUID(), price)) {
    TransactionResult result = economy.removeBalance(player.getUUID(), price);
    if (result == TransactionResult.SUCCESS) {
        // 玩家客户端上的数字已经更新了。不要再自己发一份。
    }
}
```

每一次成功的变动都会触发 `BalanceChangedEvent` 并通知玩家客户端，两件事都发生在经济内部的**同一个地方**。
绕过它直接写余额的附属模组，会**同时破坏这两件事**，而且是静默地破坏。

### 在第二页占一个位置

```java
FeaturePlaces.register(3, FeaturePlaces.of(
        Component.translatable("mymod.place.mine"),
        new ItemStack(Items.DIAMOND_PICKAXE),
        player -> player.sendSystemMessage(Component.literal("Hello from my mod"))));
```

下标是 0 到 31，先从左到右、再从上到下。位置已被占用时 `register` **返回 false，而不是顶掉原主人** ——
两个模组抢同一个下标是其中一个的 bug，静默地输掉会把 bug 藏起来。
注册发生在 mod 启动阶段；客户端在登录时会收到这份表。

**点击一个位置时，网络上只传那个下标。** 服务端自己查出它是什么并执行它，
所以一个位置**只需要写一次**，写在能决定事情的那一侧。

### 两条一定会咬到你的规矩

* **不要在 `BalanceChangedEvent` 的监听器里去调银行（或任何其他功能）。**
  事件是在**余额锁释放之后**才触发的，这正是为了让这件事安全 ✓
  但**反过来的顺序不安全** ✗：银行是先拿自己的锁、再拿余额锁 ✓
  而从一个余额回调里去够银行，就是**反过来** ✓
* **不要在 `ModNetwork` 注册表的中间新增或移动网络包。** 消息 id 是按位置分配的 ✓
  **只能往后追加** ✗ 否则用不同版本编译的客户端和服务端，会对"第三个包是什么"产生分歧 ✓
