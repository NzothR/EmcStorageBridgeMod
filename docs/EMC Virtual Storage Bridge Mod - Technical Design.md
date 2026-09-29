# EMC Virtual Storage Bridge Mod 技术设计文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | EMC Virtual Storage Bridge Mod 技术设计文档 |
| 目标游戏版本 | Minecraft 1.20.1 |
| Mod Loader | Forge |
| 开发语言 | Java |
| 核心依赖 | ProjectE |
| 一期集成目标 | Applied Energistics 2, Refined Storage |
| 兼容验证目标 | RS Integration / 共振存储, Project Expansion : Arcanum |
| P3 可选方向 | BeyondDimensions / 超越维度 |

本 Mod 的目标是将 ProjectE 的个人 EMC 与 Knowledge 包装为 AE2 / RS 可以直接访问的虚拟存储后端，使玩家能够在存储网络中像访问普通物品存储一样访问等价交换空间。

核心定位：

> ProjectE Account 是真实空间，AE2 Cell / RS Interface 只是访问入口；物流顺序交给 AE2 / RS，EMC 与 Knowledge 规则交给 ProjectE。

## 2. 背景与问题定义

### 2.1 当前常见方案

当前整合包中常见的接入方式通常是：

```text
ProjectE / Project Expansion
        ↓
Transmutation Interface
        ↓
AE2 Storage Bus / RS External Storage
        ↓
AE2 / RS Network
```

这种方案可以工作，但本质上是将 ProjectE 的转化空间伪装成一个非常大的普通库存。

### 2.2 性能问题

ProjectE 的核心状态非常简单：

- EMC 余额是一个数值；
- Knowledge 是玩家已经学习的物品集合；
- 一个物品的可取数量约等于 `currentEmc / itemEmcValue`。

但当它被包装成传统库存时，网络看到的是大量“虚拟物品堆”。一旦 EMC 发生变化，理论上所有已学习物品的可用数量都会变化。

这会导致一个结构性问题：

```text
O(1) EMC 数值变化
        ↓
被展开成
O(N) 虚拟库存变化
```

其中 `N` 是玩家已学习物品数量。大型整合包中，`N` 可能达到数千甚至上万。

### 2.3 新方案目标

本项目不再依赖 Transmutation Interface + Storage Bus / External Storage 的巨大普通库存模型，而是直接对接 AE2 / RS 的原生存储 API，并在内部把 ProjectE Account 暴露为虚拟存储空间。

设计重点：

- 高频 EMC 变化不得引发全量库存刷新；
- 显示层可以最终一致；
- 真实插入 / 提取事务必须强一致；
- 不自行实现物流路由；
- 不自行重写 ProjectE 的 EMC 与 Knowledge 规则。

## 3. 设计目标与非目标

### 3.1 设计目标

1. 将 ProjectE Personal EMC + Personal Knowledge 表现为 AE2 / RS 可访问的虚拟存储空间。
2. 支持多个不同玩家的 EMC Space 同时挂入同一个 AE2 / RS 网络。
3. 支持多个网络同时访问同一个玩家的 EMC Space。
4. 插入合法 EMC 物品时，将物品转化为 EMC。
5. 插入未学习但 ProjectE 允许学习的物品时，自动学习并转化为 EMC。
6. 提取物品时，根据真实 EMC 与 Knowledge 计算可提取数量，并正确扣除 EMC。
7. 支持 NBT 接受 / 拒绝策略。
8. 支持 AE2 / RS 原生优先级、过滤器、插入模式、提取模式和自动合成。
9. 显示缓存采用分段刷新，避免 EMC 高频变化导致全量更新。
10. 实际请求量支持超过 2.1G，内部以 `long` 级别处理。
11. 同一逻辑网络中，同一个 EMC Space 只允许一个有效入口，避免重复统计和多入口策略冲突。

### 3.2 非目标

第一版不实现：

- Team EMC；
- Shared Knowledge；
- 自定义 EMC 规则；
- 重写 ProjectE 转化逻辑；
- 自己实现 AE2 / RS 物流优先级；
- 自己实现跨 Provider reservation 系统；
- 多个同账户入口在同一网络内同时生效；
- BigInteger 级别超大物品请求；
- BeyondDimensions 原生虚拟库存接入；
- 独立 GUI 性能监控面板。

## 4. 核心概念模型

### 4.1 EMC Space

`EMC Space` 是本项目对 ProjectE 个人转化空间的抽象：

```text
EMC Space
=
Player UUID
+
ProjectE Personal EMC
+
ProjectE Personal Knowledge
```

它不是本 Mod 自己创建的新存储空间。真实数据仍然由 ProjectE 保存和维护。

本 Mod 对 EMC Space 的职责是：

- 查询当前 EMC；
- 查询 Knowledge；
- 调用 ProjectE 学习逻辑；
- 调用 ProjectE 消耗 / 增加 EMC 逻辑；
- 将这些能力包装给 AE2 / RS。

### 4.2 Access Entry

`Access Entry` 是 AE2 / RS 网络访问某个 EMC Space 的入口。

一期设计中包含两类入口：

| 平台 | 入口 |
| --- | --- |
| AE2 | EMC Storage Cell |
| RS | EMC Interface |

入口自身只保存：

- owner UUID；
- NBT policy；
- 必要配置；
- 运行时网络挂载状态。

入口自身不保存：

- 物品；
- EMC；
- Knowledge；
- 独立库存；
- 独立余额缓存。

### 4.3 同空间单入口规则

为了遵循 KISS 原则并避免重复库存统计，同一逻辑网络中，同一个 EMC Space 只允许一个有效入口。

示例：

```text
AE Grid

Cell A → Player X    Active
Cell B → Player X    Duplicate / Disabled
Cell C → Player Y    Active
```

`Cell A` 和 `Cell B` 指向同一个玩家，因此只有一个生效。`Cell C` 指向另一个玩家，因此正常生效。

这个机制称为：

```text
Duplicate Entry Suppression
```

规则：

1. 同一逻辑网络内，同一 owner UUID 只允许一个 Active Entry。
2. 第一个有效加入网络的入口成为 Active Entry。
3. 后续同 owner UUID 的入口进入 Duplicate / Disabled 状态。
4. Duplicate Entry 不上报库存，不接受插入，不参与提取。
5. Active Entry 移除后，从同一网络中剩余同 owner UUID 入口里按稳定规则选出新的 Active Entry。
6. 切换 Active Entry 不移动 EMC，不移动 Knowledge，不改变 ProjectE Account。

玩家可见状态建议：

```text
Status: Duplicate EMC Entry
Another entry for this EMC Space is already active in this network.
```

### 4.4 多空间规则

不同玩家的 EMC Space 是不同真实后端：

```text
Network
├─ Entry A → Alice EMC Space
├─ Entry B → Bob EMC Space
└─ Entry C → Charlie EMC Space
```

这些入口都可以同时生效，并交给 AE2 / RS 按普通存储规则聚合与路由。

## 5. 总体架构

```text
                 ProjectE
                    │
                    ▼
          ┌───────────────────────┐
          │ EMC Storage Core      │
          ├───────────────────────┤
          │ Account Service       │
          │ Knowledge Cache       │
          │ EMC Value Cache       │
          │ Display Amount Cache  │
          │ Transaction Core      │
          │ Entry Registry        │
          └───────────┬───────────┘
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
   AE2 Integration          RS Integration
          │                       │
   EMC Storage Cell         EMC Interface
```

架构分为三层：

| 层级 | 职责 |
| --- | --- |
| ProjectE Integration | 封装 ProjectE API，作为 EMC 和 Knowledge 的唯一规则源 |
| EMC Storage Core | 提供账户访问、缓存、事务、重复入口抑制等通用逻辑 |
| AE2 / RS Adapter | 适配具体存储网络 API，不直接修改 EMC |

核心原则：

- AE2 Adapter 和 RS Adapter 不各自实现 EMC 逻辑；
- 所有 EMC 修改集中通过 Transaction Core；
- 所有 ProjectE 规则判断集中通过 ProjectE Integration；
- Adapter 只负责把网络请求翻译为核心层调用。

## 6. ProjectE 集成层

### 6.1 Account 查询

通过玩家 UUID 获取 ProjectE 账户能力：

- Personal EMC；
- Personal Knowledge；
- 学习能力；
- 转化能力。

需要调研 ProjectE 1.20.1 Forge API 中：

- 在线玩家账户访问方式；
- 离线玩家账户访问方式；
- Capability 生命周期；
- 服务端存档加载时机；
- 保存与同步行为。

### 6.2 EMC Value 查询

封装 ProjectE 的 EMC Mapping 查询。

输入：

```text
ItemStack / ItemKey
```

输出：

```text
emcValue: long
```

无 EMC 或不可转化时返回明确失败状态，而不是返回 `0` 后继续事务。

建议内部使用结果类型：

```java
sealed interface EmcValueResult {
    record Present(long emc) implements EmcValueResult {}
    record Missing(String reason) implements EmcValueResult {}
}
```

具体 Java 版本若不适合 sealed，可用普通 enum + class 替代。

### 6.3 Knowledge 查询

Knowledge 决定该物品是否属于当前 EMC Space。

基本判断：

```text
CanTransmute(ownerUuid, itemKey)
```

若物品有 EMC，但玩家尚未学习：

- 提取时不可用；
- 插入时可尝试学习；
- 学习成功后再转化为 EMC。

### 6.4 自动学习

合法物品插入时流程：

```text
Network inserts Item
        ↓
NBT policy check
        ↓
ProjectE EMC legality check
        ↓
Knowledge check
        ↓
If unknown and learnable: ProjectE learn
        ↓
Item → EMC
        ↓
Knowledge Cache mark dirty / delta update
```

自动学习必须使用 ProjectE 原生规则。

本 Mod 不自行判断：

- 黑名单；
- EMC 合法性；
- 物品是否危险；
- NBT 是否应该折叠；
- 玩家是否允许学习。

### 6.5 ProjectE 作为唯一规则源

本 Mod 不复制 ProjectE 的核心规则表。凡是涉及 EMC 合法性、Knowledge、学习、转化的判断，均以 ProjectE 实际 API 返回值为准。

这样可以避免：

- 与 ProjectE 配置不同步；
- 与数据包 EMC 映射不同步；
- 与其他 ProjectE 扩展 Mod 冲突；
- 自己维护黑名单造成行为偏差。

## 7. 数值边界与超大请求

这一章是 AE2 / RS 集成中的关键边界设计。

### 7.1 显示数量上限

终端显示、网络库存快照、普通 UI 展示中，单个物品的显示数量不需要也不应该无限增长。

显示层采用上限：

```text
MAX_DISPLAY_AMOUNT = 2_147_483_647
```

也就是约 `2.1G`。

原因：

- 多数 Minecraft / AE2 / RS UI 不适合展示更大数量；
- 很多 API、排序、显示组件仍然以 int 为核心；
- 过大的显示值对玩家决策意义有限；
- capped display 可以避免 UI / Adapter 层溢出。

显示计算：

```text
rawAvailable = currentEmc / itemEmc
displayAmount = min(rawAvailable, MAX_DISPLAY_AMOUNT)
```

### 7.2 真实事务请求上限

某些 AE2 版本，尤其是格雷科技整合包常见的 AE2 分支或扩展环境，可能允许请求远超 2.1G 的物品量。

ProjectE 本身的 EMC 存储能力也可能远超 2.1G 对应的物品数量。

因此真实插入 / 提取事务不得使用 `int` 作为内部数量上限。

事务层统一使用：

```text
long
```

概念上可视为：

```text
MAX_TRANSACTION_AMOUNT = Long.MAX_VALUE
```

在 C++ 语义中相当于 `long long` 量级。

第一版不使用 BigInteger。理由：

- `long` 已覆盖绝大多数整合包实际需求；
- BigInteger 会显著提高实现复杂度；
- AE2 / RS / Minecraft 物品栈接口最终仍会分批落到有限数量的 ItemStack；
- 若上游 API 本身不支持 BigInteger，引入 BigInteger 无法形成端到端收益。

### 7.3 显示值与事务值分离

必须明确区分：

| 类型 | 用途 | 上限 |
| --- | --- | --- |
| DisplayAmount | 终端显示、网络快照、排序、提示 | 2.1G |
| TransactionAmount | insert / extract / simulate 的真实请求量 | `Long.MAX_VALUE` |

禁止用 DisplayAmount 限制真实提取。

错误示例：

```text
显示 Diamond = 2.1G
请求 Diamond = 10G
实际只允许取 2.1G
```

正确行为：

```text
显示 Diamond = 2.1G capped
请求 Diamond = 10G
Transaction Core 按真实 EMC 计算最多可取数量
若 EMC 足够，则允许提取 10G
```

### 7.4 溢出保护

提取时常见计算：

```text
cost = amount * itemEmc
```

该计算必须防止 long 溢出。

建议使用安全计算顺序：

```text
maxByEmc = currentEmc / itemEmc
actualAmount = min(requestedAmount, maxByEmc)
cost = actualAmount * itemEmc
```

由于 `actualAmount <= currentEmc / itemEmc`，理论上 `cost <= currentEmc`，只要 `currentEmc` 能被 ProjectE API 正确表示，乘法溢出风险会显著降低。

仍建议提供安全乘法函数：

```java
static OptionalLong safeMultiply(long a, long b)
```

当 ProjectE 的 EMC 类型不是 long，而是更大数值类型时，需要在 ProjectE Integration 层集中适配，不允许 Adapter 层各自处理。

### 7.5 ItemStack 分批生成

即使 TransactionAmount 允许 `long`，Minecraft 实际发放物品仍然需要拆分为 ItemStack。

例如请求：

```text
Diamond × 10_000_000_000
```

Adapter 不能一次构造一个超大 ItemStack。应按照 AE2 / RS API 允许的返回模型处理：

- 如果 API 本身只允许单次返回有限 ItemStack，则单次调用返回本 API 能表达的最大数量；
- 如果 API 支持 long amount 的 key-based stack，则按 API 原生 long 语义返回；
- 若网络连续请求，由网络层继续调度；
- Transaction Core 只负责保证每次调用的扣费正确。

### 7.6 测试要求

必须覆盖：

- 显示数量超过 2.1G 时终端仍显示 capped 值；
- 请求 2.1G 以内数量正常；
- 请求超过 2.1G 数量正常；
- 请求接近 `Long.MAX_VALUE` 时不会溢出；
- `itemEmc` 很大时不会溢出；
- EMC 不足时返回实际可取数量；
- simulate 超大请求不扣 EMC；
- execute 超大请求正确扣 EMC。

## 8. 核心数据结构

建议核心层对象如下：

```java
record EmcAccountId(UUID ownerUuid) {}

record ItemKey(ResourceLocation itemId, @Nullable NormalizedTag tag) {}

record TransactionRequest(
    EmcAccountId accountId,
    ItemKey itemKey,
    long amount,
    TransactionMode mode
) {}

enum TransactionMode {
    SIMULATE,
    EXECUTE
}
```

核心服务：

| 对象 | 职责 |
| --- | --- |
| `EmcAccountService` | 根据 UUID 访问 ProjectE Account |
| `EmcValueService` | 查询并缓存物品 EMC value |
| `KnowledgeService` | 查询并缓存玩家 Knowledge |
| `DisplayAmountCache` | 维护 capped 展示数量 |
| `EmcTransactionService` | 执行 insert / extract / simulate |
| `EntryRegistry` | 跟踪网络内 Active / Duplicate Entry |

### 8.1 不作为权威缓存的内容

以下内容不能缓存为独立真值：

```text
EMC Balance
```

真实事务必须在执行阶段重新读取 ProjectE 当前状态。

原因：

- 多网络可能同时访问同一账户；
- ProjectE 自身也可能改变 EMC；
- 其他 Mod 可能改变 EMC；
- 玩家可通过转化桌等方式改变 EMC；
- 显示缓存可能陈旧，但事务不能陈旧。

## 9. Knowledge Cache

### 9.1 缓存内容

Knowledge Cache 面向某个 owner UUID，缓存该玩家已学习物品。

可选结构：

```text
owner UUID
    ↓
Known ItemKey Set
```

或：

```text
owner UUID + ItemKey → known / unknown
```

具体取决于 ProjectE API 是否能高效枚举完整 Knowledge。

### 9.2 初始化

入口第一次成为 Active Entry 时初始化 Knowledge Cache。

如果完整枚举成本较高，可以采用懒加载：

- 终端显示需要枚举时加载完整集合；
- 事务请求单个物品时查询单 key；
- 后台逐步补全。

### 9.3 更新节点

Knowledge Cache 在以下事件中失效或更新：

- 自动学习成功；
- ProjectE Knowledge 变化；
- datapack / EMC mapping reload；
- 玩家登录；
- 存档加载；
- 入口重新绑定；
- Active Entry 激活；
- 必要的周期校验。

### 9.4 Knowledge 与 Availability 分离

必须明确：

```text
Knowledge 决定物品是否属于这个 EMC Space
EMC Balance 决定当前实际能提取多少
```

一个物品即使已学习，也可能因为当前 EMC 不足而实际可提取数量为 0。

## 10. EMC Value Cache

EMC Value Cache 缓存：

```text
ItemKey → emcValue
```

它的目标是避免大量重复查询 ProjectE EMC Mapping。

### 10.1 缓存失效

以下情况应清空或局部失效：

- datapack reload；
- ProjectE EMC mapping reload；
- 服务器启动后首次查询；
- 配置变化；
- 其他 Mod 动态修改 EMC 映射。

### 10.2 无 EMC 项

无 EMC 的物品也可以缓存为 Missing，以避免重复查询。

但 Missing 状态必须随 mapping reload 一起失效。

## 11. Display Amount Cache

Display Amount Cache 是性能设计核心。

### 11.1 定位

Display Amount Cache 服务于：

- AE2 终端显示；
- RS Grid 显示；
- 网络库存快照；
- 自动合成规划提示；
- 排序和搜索。

它不是权威状态。

### 11.2 分段刷新

玩家已学习物品数量可能很大，因此不能在每次 EMC 变化时全量刷新。

配置：

```text
displayRefreshBudgetPerTick = 32
```

示例：

```text
Known Items = 6000
Budget = 32 keys/tick
Full refresh ~= 188 ticks
```

显示层允许短时间陈旧。

### 11.3 单 Key 即时刷新

以下场景需要对单个 key 即时计算：

- extract；
- simulateExtract；
- 自动合成实际材料查询；
- 外部自动化请求；
- 玩家点击提取某物品；
- 事务即将执行。

即时计算只针对参与事务的 key，不触发全量刷新。

### 11.4 最终一致与强一致

项目核心原则：

```text
显示层：Eventually Consistent
事务层：Strongly Consistent
```

Display Cache 可以旧。

Transaction Core 不能旧。

## 12. Transaction Core

Transaction Core 是唯一允许修改 EMC 的核心服务。

AE2 Adapter 和 RS Adapter 必须通过它执行操作。

### 12.1 Insert 流程

```text
Network inserts Item × N
        ↓
Active Entry check
        ↓
NBT policy check
        ↓
ProjectE EMC legality check
        ↓
Knowledge check
        ↓
If unknown: try learn
        ↓
amount × itemEmc → EMC gain
        ↓
ProjectE add EMC
        ↓
update / invalidate related cache
```

若入口是 Duplicate / Disabled，直接拒绝。

若 NBT policy 是 Reject 且物品携带 NBT，直接拒绝，让 AE2 / RS 继续尝试其他存储后端。

### 12.2 Extract 流程

```text
Network requests Item × requestedAmount
        ↓
Active Entry check
        ↓
Knowledge check
        ↓
EMC value check
        ↓
real EMC query
        ↓
maxByEmc = currentEmc / itemEmc
        ↓
actualAmount = min(requestedAmount, maxByEmc)
        ↓
if EXECUTE: consume actualAmount × itemEmc
        ↓
return actualAmount
```

`requestedAmount` 使用 long。

`actualAmount` 使用 long。

禁止在事务层套用 2.1G display cap。

### 12.3 SIMULATE

SIMULATE 不得修改：

- EMC；
- Knowledge；
- 物品库存；
- ProjectE Account；
- 权威状态；
- Active Entry 状态。

SIMULATE 可以：

- 查询 ProjectE；
- 刷新单 key display cache；
- 返回当前单次操作可行结果。

多次 SIMULATE 不形成 reservation。

示例：

```text
Account has EMC for 100 Diamond

simulateExtract(80) → 80
simulateExtract(80) → 80
```

第二次仍然可以返回 80，因为第一次模拟没有消耗 EMC。

### 12.4 EXECUTE

EXECUTE 必须重新读取真实状态，不能相信之前 SIMULATE 的结果。

示例：

```text
simulateExtract(100) → 100
其他网络先取走 80
executeExtract(100) → 20
```

这不是错误，而是强一致事务的正确行为。

### 12.5 Partial Extraction

当请求量大于可用量：

```text
request = 64
available = 20
return = 20
```

AE2 / RS 自己决定是否继续访问下一个存储后端。

本 Mod 不决定下一站。

### 12.6 Insert 的部分接受

若插入物品数量过大，ProjectE EMC 增加可能遇到上限或 API 限制。

需要调研 ProjectE 是否存在 EMC 上限：

- 若存在上限，插入应只接受可转化部分；
- 若无上限，则接受全部合法部分；
- 若 ProjectE API 不支持部分增加，需要在 Integration 层封装出明确行为。

## 13. 并发与防复制设计

### 13.1 多网络访问同一账户

必须允许：

```text
AE Grid A ─┐
AE Grid B ─┼→ Player X EMC Space
RS Grid C ─┘
```

这些是不同逻辑网络访问同一个 ProjectE Account。

### 13.2 同时 Extract

所有 extract 都必须经过 Transaction Core，并在执行时读取真实 ProjectE EMC。

如果 Forge / ProjectE 服务器线程模型保证相关调用都在主线程串行执行，则可以不加显式锁。

如果 AE2 / RS 存在异步调用路径，则需要 Account-level lock 或主线程调度。

### 13.3 禁止 Adapter 直接修改 EMC

错误结构：

```text
AE2 Adapter → ProjectE add/remove EMC
RS Adapter  → ProjectE add/remove EMC
```

正确结构：

```text
AE2 Adapter ─┐
             ├→ Transaction Core → ProjectE Integration
RS Adapter  ─┘
```

这样才能集中处理：

- 溢出；
- simulate / execute；
- 缓存失效；
- 并发；
- 日志；
- 错误码；
- 兼容差异。

## 14. NBT 策略

### 14.1 模式

第一版提供两种模式：

| 模式 | 行为 |
| --- | --- |
| Reject NBT | 携带 NBT 的物品不进入 EMC Space |
| Allow NBT | 允许进入，但最终仍交给 ProjectE 原生规则 |

### 14.2 Reject NBT

若入口配置为 Reject NBT：

```text
ItemStack has NBT
        ↓
return cannot accept
```

网络会自然尝试其他存储后端。

本 Mod 不需要也不应该决定物品的下一站。

### 14.3 Allow NBT

Allow NBT 不代表本 Mod 会保存 NBT。

规则：

- 不保存 NBT；
- 不恢复 NBT；
- 不自行规范化；
- 不自行判断危险 NBT；
- 不自行把不同 NBT 物品拆成不同虚拟库存；
- 完全交给 ProjectE 原生逻辑。

如果 ProjectE 拒绝该 NBT 物品，则本 Mod 也拒绝。

## 15. AE2 集成设计

### 15.1 EMC Storage Cell

AE2 侧提供一种 Storage Cell：

```text
EMC Storage Cell
```

它作为 AE2 原生存储入口接入 Drive / ME Network。

### 15.2 绑定

未绑定 Cell：

```text
Player holds cell
        ↓
Right click / bind action
        ↓
ownerUuid = player UUID
```

工作中不允许重新绑定。若要换 owner，需要先从网络中取出，并通过明确操作重置或重新制作。

### 15.3 Cell 保存内容

Cell NBT 仅保存：

```text
owner UUID
NBT policy
必要配置版本
```

不保存：

```text
EMC
Knowledge
虚拟库存内容
Display Cache
```

### 15.4 Storage Adapter

AE2 Adapter 需要实现或接入 AE2 1.20.1 对应的存储接口。

职责：

- 向 AE2 暴露可用 item keys；
- 提供 capped display amount；
- 将 insert 调用转发到 Transaction Core；
- 将 extract / simulate 调用转发到 Transaction Core；
- 在 Cell 加入 / 移除网络时通知 EntryRegistry；
- 在 Duplicate 状态下不上报库存并拒绝操作。

具体接口名称需以 AE2 1.20.1 源码为准。

### 15.5 AE2 Priority

本 Mod 不实现自己的物流优先级。

AE2 决定：

```text
先访问普通盘
还是先访问 EMC Cell
还是先访问其他 Storage Bus
```

EMC Cell 只回答：

```text
我能不能接受这个物品？
我现在最多能提供多少？
这次实际给你多少？
```

### 15.6 Duplicate Entry Suppression

同一 AE Grid 中，同一 owner UUID 只允许一张 EMC Cell 生效。

后续同 owner UUID Cell：

- 状态为 Duplicate；
- 不参与库存；
- 不接受 insert；
- 不响应 extract；
- 可以在 tooltip / debug probe 中显示原因。

### 15.7 Active Cell 移除与接替

Active Cell 被移除时：

```text
Active Cell removed
        ↓
EntryRegistry unregister
        ↓
scan same-grid duplicate entries
        ↓
select next stable candidate
        ↓
candidate becomes Active
        ↓
mark network storage changed
```

稳定规则可以是：

- 注册顺序；
- 网络遍历顺序；
- 坐标排序；
- AE2 提供的稳定 identity。

实现阶段选择最符合 AE2 生命周期的方案。

## 16. Refined Storage 集成设计

### 16.1 EMC Interface

RS 侧提供一个方块：

```text
EMC Interface
```

它作为 ProjectE EMC Space 与 RS External Storage 的桥接点。

### 16.2 绑定

方块放置时：

```text
ownerUuid = placer UUID
```

后续可以考虑提供 wrench / GUI 重绑，但 MVP 不需要。

### 16.3 RS External Storage

玩家使用 RS 原生 External Storage 连接 EMC Interface。

这样可以保留 RS 原生能力：

- Priority；
- Insert / Extract Mode；
- Filter；
- 白名单 / 黑名单；
- 外部存储配置。

本 Mod 不替代 RS 的 External Storage 配置。

### 16.4 Interface 职责

EMC Interface 只负责：

```text
RS External Storage
        ↕
EMC Storage Core
```

它不保存 EMC，不保存物品，不实现自己的物流顺序。

### 16.5 Duplicate Entry Suppression

同一 RS Network + 同 owner UUID，只允许一个 EMC Interface 生效。

后续重复入口进入 Duplicate / Disabled 状态。

## 17. 普通物流语义

本 Mod 必须遵循 AE2 / RS 原生物流哲学。

示例：

```text
Priority 100: 普通 Storage
Priority  50: EMC Cell
Priority   0: 其他 Storage
```

插入物品时：

```text
Network tries normal storage
        ↓
if failed
Network tries EMC Cell
        ↓
if rejected
Network tries next storage
```

EMC Cell 只回答当前入口是否接受。

它不关心：

- 下一个存储是谁；
- 网络为什么选择它；
- 其他存储是否有空间；
- 物品最终会去哪里。

## 18. 多 EMC Space 行为

一个网络可以同时存在多个玩家空间：

```text
AE / RS Network
├─ Alice EMC Space
├─ Bob EMC Space
└─ Charlie EMC Space
```

它们是真正不同的存储后端。

网络显示可能聚合为：

```text
Diamond:
Alice can provide 100
Bob can provide 50
Charlie can provide 1000
```

显示层总量可能 capped，但每个账户的真实事务独立执行。

提取时由 AE2 / RS 按其 Provider 顺序调用不同入口。

## 19. RS Integration / 共振存储兼容

### 19.1 目标

目标不是直接依赖 RS Integration，而是保证它通过 RS 查询和取用材料时，可以正常访问 EMC Interface。

### 19.2 测试重点

必须覆盖：

- recipe material scan；
- simulate extract；
- execute extract；
- recursive craft；
- reservation；
- rollback / refund；
- EMC 不足；
- Display Cache 陈旧；
- 超大请求；
- 多账户；
- Duplicate Entry。

### 19.3 专用 Hook 原则

只有确认 RS Integration 绕过 RS 标准 Storage API 时，才增加专用 compatibility hook。

默认策略：

```text
优先兼容 RS 标准 API
        ↓
若第三方 Mod 绕过标准 API
        ↓
再考虑最小兼容层
```

## 20. Project Expansion : Arcanum 共存

Project Expansion : Arcanum 可作为共存目标，但不是本 Mod 的核心依赖。

原则：

- 不依赖 Transmutation Interface；
- 不接管 Arcanum 物品；
- 不与 Arcanum EMC 扩展 API 冲突；
- 若 Arcanum 改变 EMC 上限或数值类型，由 ProjectE Integration 层统一适配；
- Adapter 层不直接感知 Arcanum。

## 21. BeyondDimensions

BeyondDimensions 属于：

```text
P3 / Optional / Non-goal for MVP
```

原因：

- BD 已经可以接 AE2 / RS；
- 原生 EMC 接入收益不明确；
- 容易产生嵌套网络；
- 会增加大量复杂度；
- 不影响 MVP 目标验证。

未来真有需求时再单独设计。

## 22. 生命周期设计

需要覆盖以下生命周期：

| 事件 | 行为 |
| --- | --- |
| 玩家登录 | 可预热或刷新该玩家 Account 状态 |
| 玩家离线 | 根据 ProjectE 能力决定是否保留访问 |
| Cell 加入 Drive | 注册 Entry，尝试成为 Active |
| Cell 移除 Drive | 注销 Entry，触发 Duplicate 接替 |
| Drive / Grid 重载 | 重建 EntryRegistry 状态 |
| RS Interface chunk load | 注册 Entry |
| RS Interface chunk unload | 注销 Entry |
| 网络重构 | 重新判定 Active / Duplicate |
| server save | 不额外保存 EMC，只保存入口配置 |
| server stop | 清理运行时缓存 |
| datapack reload | 清空 EMC Value Cache，刷新 Knowledge / Display |
| ProjectE capability 不可用 | 入口进入 unavailable 状态 |

## 23. 异常处理

### 23.1 Owner Offline

需要调研 ProjectE 是否允许访问离线玩家账户。

若允许：

```text
正常访问离线 owner 的 EMC Space
```

若不允许：

```text
storage temporarily unavailable
```

不能崩溃，不能清空入口配置。

### 23.2 EMC Mapping Missing

物品没有 EMC：

- insert 拒绝；
- extract 不上报；
- simulate 返回 0；
- 日志可在 debug 模式记录。

### 23.3 Account Invalid

owner UUID 无效或账户不存在：

- Entry 状态设为 Unavailable；
- 不参与库存；
- 不接受事务；
- tooltip 显示原因。

### 23.4 Cache Failure

缓存构建失败时：

- 不影响 ProjectE Account；
- 允许重建；
- 不把失败缓存写成权威状态；
- Debug 日志记录异常。

### 23.5 Overflow

发现数量计算溢出风险时：

- display 层 capped；
- transaction 层按安全除法计算；
- 无法安全表达时返回最大安全可处理值；
- 记录 debug 日志；
- 不生成负数数量；
- 不扣除错误 EMC。

## 24. 性能约束

### 24.1 禁止行为

禁止：

```text
EMC change
        ↓
iterate all known items immediately
```

禁止：

```text
每 tick 全量刷新所有 Knowledge display amount
```

禁止：

```text
Adapter 层为每个物品重复查询 ProjectE mapping
```

### 24.2 允许行为

允许：

```text
EMC change → O(1) dirty mark
```

允许：

```text
Display refresh → budgeted incremental update
```

允许：

```text
Transaction single key → immediate exact calculation
```

### 24.3 目标复杂度

| 操作 | 目标复杂度 |
| --- | --- |
| EMC 数值变化 | O(1) |
| 单物品 extract | O(1) 或接近 O(1) |
| 单物品 insert | O(1) 或接近 O(1) |
| Display 全量刷新 | O(N)，但分 tick 限流 |
| Knowledge 完整加载 | O(N)，只在必要时发生 |
| Duplicate Entry 判定 | O(entries in network)，仅生命周期变化时发生 |

## 25. 配置设计

MVP 只保留必要配置：

```toml
[display]
refreshBudgetPerTick = 32
maxDisplayAmount = 2147483647

[entry]
defaultNbtPolicy = "REJECT"

[debug]
enableDebugLog = false
logTransactions = false

[compatibility]
enableRsIntegrationWorkaround = true
```

`maxDisplayAmount` 可配置，但默认固定为 2.1G。

`maxTransactionAmount` 不建议开放配置，内部使用 `Long.MAX_VALUE` 作为硬边界。

## 26. 日志与调试

Debug 日志示例：

```text
[EMCBridge] Account 7f... mounted to AE grid as active entry
[EMCBridge] Duplicate EMC entry ignored: owner=7f..., grid=...
[EMCBridge] Extract diamond x6400000000, cost=..., mode=EXECUTE
[EMCBridge] Display amount capped: diamond raw=9000000000 display=2147483647
```

默认关闭高频事务日志。

推荐日志等级：

| 等级 | 内容 |
| --- | --- |
| INFO | 版本、集成加载、关键兼容开关 |
| DEBUG | entry 注册、duplicate 抑制、缓存刷新 |
| TRACE | 高频 transaction 细节 |
| WARN | ProjectE capability 缺失、兼容异常、溢出风险 |
| ERROR | 无法恢复的 API 调用失败 |

## 27. 开发阶段与顺序

实现阶段按两个可测试兼容闭环组织：

1. 公共核心底座；
2. AE2 兼容闭环；
3. RS 兼容闭环；
4. 跨平台压力测试与发布准备。

公共核心只做到能支撑 Adapter 调用，不把它视为最终交付物。真正的阶段验收以“AE2 能在实际网络中稳定使用”和“RS 能在实际网络中稳定使用”为准。

### 27.1 公共核心底座

这一段的目标是建立 AE2 / RS 共用的最小稳定核心，避免两个 Adapter 各自实现 ProjectE 逻辑。

#### Core Phase 1：ProjectE API 调研与最小 Account Adapter

实现内容：

- 创建 Forge Mod 基础工程；
- 接入 ProjectE 依赖；
- 通过 UUID 查询玩家 EMC；
- 查询物品 EMC value；
- 查询玩家 Knowledge；
- 测试在线与离线玩家。

测试方法：

- 给玩家添加 EMC；
- 查询当前 EMC；
- 查询 diamond / iron / 无 EMC 物品；
- 查询已学习与未学习物品。

预期结果：

- 能稳定获取 ProjectE Account；
- 无 EMC 物品被明确识别；
- 离线账户行为明确记录。

#### Core Phase 2：Transaction Core

实现内容：

- insert simulate / execute；
- extract simulate / execute；
- 自动学习；
- long 数量请求；
- 溢出保护；
- partial extraction。

测试方法：

- 插入 EMC 物品；
- 插入未学习物品；
- 提取已学习物品；
- 提取 EMC 不足物品；
- simulate 后确认 EMC 不变；
- execute 后确认 EMC 正确变化；
- 请求超过 2.1G；
- 请求接近 Long.MAX_VALUE。

预期结果：

- 事务层不依赖 AE2 / RS；
- 所有 EMC 修改集中通过 Transaction Core；
- 超大请求不被 2.1G 截断；
- 不出现负数、溢出、复制。

#### Core Phase 3：Knowledge Cache + EMC Value Cache

实现内容：

- owner 级 Knowledge Cache；
- ItemKey → EMC Value Cache；
- reload 失效；
- 自动学习后的 delta update。

测试方法：

- 学习前后查询；
- datapack reload；
- ProjectE mapping 改变；
- 大量物品查询。

预期结果：

- 查询次数明显减少；
- 缓存不会成为权威错误来源；
- reload 后不会沿用旧 EMC。

#### Core Phase 4：EntryRegistry 抽象

实现内容：

- 定义通用 Entry identity；
- 定义 Active / Duplicate / Unavailable 状态；
- 定义同 network + owner UUID 去重规则；
- 为 AE2 / RS 预留不同 network identity 的适配入口。

测试方法：

- 使用伪 network id 注册多个 entry；
- 同 network + 同 owner 只激活一个；
- 同 network + 不同 owner 同时激活；
- 不同 network + 同 owner 同时激活；
- Active 移除后 Duplicate 接替。

预期结果：

- Duplicate Entry Suppression 可在无 AE2 / RS 环境下单元测试；
- 规则不依赖具体平台；
- 后续 Adapter 只需要提供 network identity 和 entry lifecycle。

### 27.2 AE2 兼容闭环

AE2 是第一条完整实现线。目标不是做一个只能显示物品的半成品，而是尽快拿到一个可在 AE2 网络中真实插入、提取、自动合成、压测的闭环版本。

AE2 阶段完成后，应当可以在只安装 ProjectE + AE2 的测试环境中独立验证本 Mod 的核心价值。

#### AE Phase 1：AE2 Storage API 调研与最小 Cell 注册

实现内容：

- 确认 AE2 1.20.1 Forge 存储 API；
- 创建 EMC Storage Cell 物品；
- 注册 Cell 类型；
- 支持 owner UUID 绑定；
- Cell 可被 AE2 Drive 识别；
- Duplicate / Unavailable 状态可在 tooltip 或 debug 信息中显示。

测试方法：

- 创建未绑定 Cell；
- 玩家绑定 Cell；
- 放入 AE2 Drive；
- 从 Drive 取出；
- 服务器重启后绑定仍存在。

预期结果：

- AE2 能识别 EMC Cell；
- Cell 持久化 owner UUID 与 NBT policy；
- 暂不要求完整库存显示，但生命周期必须稳定。

#### AE Phase 2：AE2 可见库存闭环

实现内容：

- 将 ProjectE Knowledge 暴露给 AE2；
- 接入 Display Amount Cache；
- 对显示数量执行 2.1G cap；
- 支持 EMC Value Cache；
- 网络变化时通知 AE2 刷新。

测试方法：

- 终端查看已学习物品；
- 大量 Knowledge 物品显示；
- EMC 增长后显示最终刷新；
- 单个物品超过 2.1G 时显示 capped；
- 无 EMC 物品不显示；
- 未学习物品不显示。

预期结果：

- AE2 终端可以稳定浏览 EMC Space；
- 显示层不会因 EMC 高频变化全量刷新；
- display capped 不影响后续事务设计。

#### AE Phase 3：AE2 Insert / Extract 闭环

实现内容：

- AE2 insert 转发到 Transaction Core；
- AE2 extract / simulate 转发到 Transaction Core；
- NBT policy 生效；
- long transaction amount 生效；
- partial extraction 生效。

测试方法：

- 插入 iron，确认 EMC 增加；
- 插入未学习合法物品，确认自动学习；
- 插入无 EMC 物品，确认拒绝；
- 插入 NBT 物品，分别测试 Reject / Allow；
- 提取 diamond，确认 EMC 扣除；
- EMC 不足时返回部分数量；
- 请求超过 2.1G，确认不被 display cap 截断；
- simulate 不扣 EMC。

预期结果：

- AE2 网络可以真实使用 EMC Cell；
- 所有事务以 ProjectE Account 为准；
- 不发生复制或错误扣费。

#### AE Phase 4：AE2 Duplicate Entry Suppression

实现内容：

- 将 AE2 Grid identity 接入 EntryRegistry；
- 同 AE Grid + 同 owner UUID 只允许一个 Active Cell；
- Duplicate Cell 不上报库存，不参与事务；
- Active Cell 移除后自动接替。

测试方法：

- 同一 AE 网络放入两张同 owner Cell；
- 同一 AE 网络放入不同 owner Cell；
- 两个 AE 网络分别放入同 owner Cell；
- 移除 Active Cell；
- Drive 重载；
- 服务器重启；
- 网络分裂 / 合并。

预期结果：

- 同一 AE 网络内不会重复统计同一 EMC Space；
- 不同网络仍可访问同一 EMC Space；
- Duplicate 状态可被玩家理解。

#### AE Phase 5：AE2 自动合成兼容

实现内容：

- 验证 AE2 crafting planner；
- 验证 simulate / execute；
- 处理 display cache 陈旧情况下的实际请求。
- 验证超大材料请求；
- 验证中途 EMC 变化。

测试方法：

- 自动合成小配方；
- 自动合成大批量配方；
- EMC 不足；
- 中途 EMC 被其他网络消耗；
- 超大材料请求。

预期结果：

- 自动合成不复制；
- EMC 不足时正确失败或部分执行；
- planner 不因 display capped 破坏真实请求。

#### AE Phase 6：AE2 性能与可发布验收

实现内容：

- AE2 侧分段刷新调优；
- profiler；
- 与 Transmutation Interface + Storage Bus 对比；
- 整理 AE2 已知限制。

测试方法：

- 100 / 1000 / 5000 / 10000 learned keys；
- EMC 高频增长；
- 打开终端；
- 大量 insert；
- 大量 extract；
- 自动合成；
- 超大请求。

预期结果：

- AE2 侧形成可测试、可使用、可对比性能的完整版本；
- 作为后续 RS 开发的稳定核心参考。

### 27.3 RS 兼容闭环

RS 阶段以 AE2 已验证的公共核心为基础，目标是完成 Refined Storage 的端到端兼容，而不是简单做一个能被 RS 看见的方块。

RS 阶段完成后，应当可以在只安装 ProjectE + RS 的测试环境中独立验证插入、提取、过滤、优先级和自动化行为。

#### RS Phase 1：RS Storage API 调研与 EMC Interface 方块

实现内容：

- EMC Interface 方块；
- owner 绑定；
- chunk load / unload 生命周期；
- 与 RS External Storage 的连接方式确认；
- 方块状态显示。

测试方法：

- 放置 Interface；
- owner UUID 保存与读取；
- 区块卸载 / 加载；
- 服务器重启；
- RS External Storage 能识别该方块。

预期结果：

- EMC Interface 生命周期稳定；
- 可作为 RS External Storage 后端挂入网络；
- 暂不要求完整事务，但连接链路必须打通。

#### RS Phase 2：RS 可见库存闭环

实现内容：

- 将 ProjectE Knowledge 暴露给 RS；
- 接入 Display Amount Cache；
- 显示数量 capped 到 2.1G；
- 处理 RS Grid 打开与刷新；
- 尊重 RS External Storage 的过滤与模式设置。

测试方法：

- RS Grid 查看已学习物品；
- 大量 Knowledge 物品显示；
- EMC 增长后最终刷新；
- 超过 2.1G 的物品显示 capped；
- External Storage filter 生效；
- Insert-only / Extract-only 设置生效。

预期结果：

- RS Grid 可以稳定浏览 EMC Space；
- 显示缓存不触发全量即时刷新；
- RS 原生过滤与模式不被绕过。

#### RS Phase 3：RS Insert / Extract 闭环

实现内容：

- RS insert 转发到 Transaction Core；
- RS extract / simulate 转发到 Transaction Core；
- NBT policy 生效；
- long transaction amount 生效；
- partial extraction 生效；
- priority 与 filter 完全交给 RS。

测试方法：

- 插入 iron，确认 EMC 增加；
- 插入未学习合法物品，确认自动学习；
- 插入无 EMC 物品，确认拒绝；
- 插入 NBT 物品，分别测试 Reject / Allow；
- 提取 diamond，确认 EMC 扣除；
- EMC 不足；
- 请求超过 2.1G；
- simulate 不扣 EMC；
- RS priority / filter / mode 联合测试。

预期结果：

- RS 网络可以真实使用 EMC Interface；
- Interface 不自行决定物流顺序；
- 所有事务以 ProjectE Account 为准。

#### RS Phase 4：RS Duplicate Entry 与网络生命周期

实现内容：

- 将 RS Network identity 接入 EntryRegistry；
- 同 RS Network + 同 owner UUID 只允许一个 Active Interface；
- Duplicate Interface 不上报库存，不参与事务；
- Active Interface 移除后自动接替；
- 处理 chunk unload / reload。

测试方法：

- 同网络多个同 owner Interface；
- 同网络不同 owner Interface；
- 不同 RS 网络同 owner Interface；
- 区块卸载；
- RS Controller 重启；
- External Storage 重连；
- 服务器重启。

预期结果：

- RS 内不会重复统计同一 EMC Space；
- 不同网络仍可访问同一 EMC Space；
- 生命周期稳定，不丢绑定。

#### RS Phase 5：RS Integration / 共振存储兼容

实现内容：

- 跑通 RS Integration 典型自动化链；
- 观察是否走 RS 标准 Storage API；
- 必要时添加最小兼容 hook；
- 验证 rollback / refund 语义。

测试方法：

- 材料扫描；
- simulate；
- execute；
- rollback；
- recursive craft；
- EMC 不足；
- display cache 陈旧；
- 超大请求。

预期结果：

- 默认通过 RS 标准路径工作；
- 若需 hook，hook 范围最小。

#### RS Phase 6：RS 性能与可发布验收

实现内容：

- RS 侧分段刷新调优；
- profiler；
- 与 Transmutation Interface + External Storage 对比；
- 整理 RS 已知限制。

测试方法：

- 100 / 1000 / 5000 / 10000 learned keys；
- EMC 高频增长；
- 打开 RS Grid；
- 大量 insert；
- 大量 extract；
- RS Integration 自动化；
- 超大请求。

预期结果：

- RS 侧形成可测试、可使用、可对比性能的完整版本；
- AE2 与 RS 共享同一核心，不出现行为分叉。

### 27.4 跨平台最终验证

实现内容：

- 多 owner；
- 多 AE Grid；
- 多 RS Network；
- AE + RS 同时访问同一 ProjectE Account。

测试方法：

- Alice / Bob 同网络；
- Alice 同时挂 AE 和 RS；
- 两个网络同时提取；
- Player offline；
- 权限边界检查。

预期结果：

- 不串账户；
- 不复制；
- 不因一个网络 duplicate 影响另一个网络；
- 多网络访问同一账户保持真实状态。

### 27.5 性能测试与优化

实现内容：

- 基准场景；
- profiler；
- 与 Transmutation Interface 方案对比。

测试场景：

| Known Items | 场景 |
| --- | --- |
| 100 | 基础功能 |
| 1000 | 普通整合包 |
| 5000 | 大型整合包 |
| 10000 | 压力测试 |

测试指标：

- idle MSPT；
- EMC 高频增长；
- terminal 打开耗时；
- 大量 extract；
- 大量 insert；
- autocrafting；
- 多网络；
- 多账户。

预期结果：

- EMC 高频变化成本接近 O(1)；
- 分段刷新受 budget 控制；
- 性能明显优于 Transmutation Interface + Storage Bus / External Storage。

### 27.6 发布准备

实现内容：

- 配置文件整理；
- 日志等级整理；
- tooltip / 状态提示；
- README；
- 已知限制；
- 兼容矩阵；
- 崩溃保护。

预期结果：

- MVP 可在目标整合环境中长期运行；
- 玩家能理解 Duplicate 状态；
- 开发者能定位事务和缓存问题。

## 28. 性能基准测试设计

### 28.1 对照组

对照组：

```text
ProjectE / Project Expansion
        +
Transmutation Interface
        +
AE2 Storage Bus / RS External Storage
```

实验组：

```text
EMC Virtual Storage Bridge
```

### 28.2 指标

| 指标 | 说明 |
| --- | --- |
| Idle MSPT | 无操作时服务器 tick 成本 |
| EMC Update Cost | EMC 高频变化时的成本 |
| Terminal Open Time | 打开 AE2 / RS 终端耗时 |
| Display Refresh Cost | 分段刷新实际消耗 |
| Extract Throughput | 大量提取吞吐 |
| Insert Throughput | 大量插入吞吐 |
| Autocrafting Stability | 自动合成稳定性 |
| Memory Usage | 缓存占用 |

### 28.3 通过标准

至少应满足：

- EMC 每 tick 增长时不会导致明显 MSPT 尖峰；
- display refresh 成本受 budget 限制；
- terminal 打开不会因瞬间全量计算卡死；
- 超大请求不会溢出；
- 性能优于传统 Transmutation Interface 方案。

## 29. MVP 完成标准

MVP 至少满足：

- AE2 Cell 可绑定 owner；
- AE2 Cell 可挂入网络；
- ProjectE Knowledge 可显示；
- 显示数量 capped 到 2.1G；
- 真实提取支持超过 2.1G 的请求；
- 插入合法物品可转 EMC；
- 未学习合法物品可自动学习；
- 提取正确扣 EMC；
- simulate 不扣 EMC；
- NBT policy 正常；
- Duplicate Entry Suppression 正常；
- Display Cache 不因 EMC 每 tick 变化而全量刷新；
- AE2 自动合成基本正常；
- RS Interface 可接入 External Storage；
- RS Grid 可正常插入 / 提取；
- 多账户正常；
- 多网络访问同账户正常；
- 无明显复制 bug；
- 性能明显优于传统方案。

## 30. 后续扩展

MVP 后可考虑：

- BeyondDimensions；
- Team EMC；
- Shared Knowledge；
- 更多存储 Mod；
- 更高级动态 refresh scheduler；
- 性能监控 GUI；
- 更丰富 Cell 配置；
- 第三方 Mod API；
- BigInteger 级别事务支持。

BigInteger 只在以下条件同时成立时考虑：

1. ProjectE 或扩展 Mod 实际使用 BigInteger 级 EMC；
2. AE2 / RS 或目标 API 可表达 BigInteger 请求；
3. 真实整合包场景能触及 long 上限；
4. 引入 BigInteger 后能形成端到端收益。

在此之前，`long` 是性能、复杂度和实用性的平衡点。

## 31. 核心原则总结

最终设计原则如下：

1. ProjectE Account 是空间。
2. AE2 Cell / RS Interface 是入口。
3. 同网络同空间只允许一个有效入口。
4. 不同空间可以同时存在并由网络自然聚合。
5. 物流顺序完全交给 AE2 / RS。
6. ProjectE 是 EMC 与 Knowledge 的唯一规则源。
7. 显示缓存可以陈旧。
8. 真实事务必须强一致。
9. 显示最大值 capped 到 2.1G。
10. 实际请求量使用 long，允许超过 2.1G。
11. EMC 高频变化不能触发全量库存刷新。
12. SIMULATE 不做 reservation。
13. EXECUTE 必须重新读取真实状态。
14. Adapter 不直接修改 EMC。
15. 任何防复制逻辑都集中在 Transaction Core 与 EntryRegistry。
