# EMC Storage Bridge

EMC Storage Bridge 将 ProjectE 的个人 EMC 与已学习物品接入 AE2 和 Refined Storage，让玩家可以直接在网络终端浏览、存入和取出 EMC 物品。

## 版本与依赖

- Minecraft 1.20.1
- Minecraft Forge 47.x
- Java 17
- ProjectE 1.0.1 或更高版本（必需）
- Applied Energistics 2（可选）：提供 EMC 存储元件
- Refined Storage 1.12.x（可选）：提供 EMC 接口方块

AE2 与 RS 是独立的软依赖：可以只安装其中一个，也可以同时安装；未安装的平台不会注册对应物品和集成。

## 使用方法

### AE2

1. 手持 EMC 存储元件右键，将它绑定给自己。
2. 将存储元件放入 ME 驱动器。
3. 绑定后按住 Shift 右键可切换该元件是否接受带 NBT 的物品。
4. 在 ME 终端中浏览 EMC 物品，并通过终端存入或取出物品。

### Refined Storage

1. 放置 EMC 接口，接口会绑定给放置者。
2. 将 RS 外部存储朝向 EMC 接口，并连接到 RS 网络。
3. 接口所有者手持贤者之石右键接口，可切换是否接受带 NBT 的物品。
4. 在 RS Grid 中浏览 EMC 物品，并通过终端存入或取出物品。

RS 外部存储的过滤器、优先级和访问模式仍由 RS 设置。每个网络内，同一玩家的 EMC 空间只通过一个有效入口提供，避免重复显示和重复访问。

## 显示与事务

- 终端显示数量上限默认为 `2,147,483,647`；实际插入和提取按真实 EMC 余额及物品 EMC 值计算，使用 `long` 数量，不受显示上限影响。
- 显示库存采用增量刷新。知识变化或 EMC 余额变化后，物品列表最终一致；实际交易会重新读取 ProjectE 账户状态。
- ProjectE 的“全知识”状态会展开为显式 EMC 映射知识，以便两个终端完整显示物品，并允许在转化桌逐项遗忘。该操作会增加玩家保存的知识条目数量。
- 默认拒绝带 NBT 的物品；可在元件或接口上切换策略。

## 配置

配置文件位于 `config/emcstoragebridge-common.toml`：

```toml
[display]
refreshRoundTicks = 200
refreshIntervalTicks = 0
maxDisplayAmount = 2147483647

[entry]
defaultNbtPolicy = "REJECT"

[debug]
enableDebugLog = false
logTransactions = false
```

`refreshRoundTicks` 是一轮完整显示刷新的目标时长，单位为 tick；`refreshIntervalTicks` 是同一轮中两次刷新之间等待的 tick 数。间隔为 `0` 时每 tick 刷新一次。模组根据已学习物品数和一轮内的刷新次数，动态计算每次处理的物品数。

## 开发

使用 IntelliJ IDEA 打开本目录并作为 Gradle 项目导入。使用 Java 17。

```powershell
.\gradlew.bat build
```

主要功能闭环已实现，AE2 与 RS 的基础操作此前已由用户在游戏内验证。本轮刷新调度、全知识展开与贴图改动尚未经过游戏内回归；大型知识列表、长时间运行及性能验证仍待完成。已知待办见[开发完成报告与待办](docs/开发完成报告与待办.md)，架构和验收设计见[技术设计文档](docs/EMC%20Virtual%20Storage%20Bridge%20Mod%20-%20Technical%20Design.md)。
