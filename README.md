# EMC Storage Bridge

EMC Storage Bridge 将 ProjectE 的个人 EMC、已学习物品和装液桶对应的流体接入 AE2 与 Refined Storage，让玩家可以直接在网络终端浏览、存入物品并取出 EMC 物品或流体。

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
3. 绑定后按住 Shift 右键可切换是否接受带 NBT 的输入。允许时按原始物品栈的 ProjectE EMC 值计入；存入时用 ProjectE 持久 NBT 规则学习，终端显示和提取依据 Knowledge 中的精确物品键，与转化桌一致。
4. 在 ME 终端中浏览 EMC 物品和流体，并通过终端存入物品或取出物品、流体。

### Refined Storage

1. 放置 EMC 接口，接口会绑定给放置者。
2. 将 RS 外部存储朝向 EMC 接口，并连接到 RS 网络。
3. 接口所有者手持贤者之石右键接口，可切换是否接受带 NBT 的输入。允许时按原始物品栈的 ProjectE EMC 值计入；存入时用 ProjectE 持久 NBT 规则学习，终端显示和提取依据 Knowledge 中的精确物品键，与转化桌一致。
4. RS 外部存储节点一次只能使用一种存储类型。要同时接入物品和流体，请将物品型与流体型外部存储分别连接到 EMC 接口的不同侧。
5. 在 RS Grid 中浏览 EMC 物品和流体，并通过终端存入物品或取出物品、流体。

RS 外部存储的过滤器、优先级和访问模式仍由 RS 设置。每个网络内，同一玩家的 EMC 空间只通过一个有效入口提供，避免重复显示和重复访问。

## 显示与事务

- 终端显示数量上限默认为 `2,147,483,647`；实际插入和提取按真实 EMC 余额及物品 EMC 值计算，使用 `long` 数量，不受显示上限影响。
- 显示库存采用增量刷新。知识变化或 EMC 余额变化后，物品列表最终一致；实际交易会重新读取 ProjectE 账户状态。
- ProjectE 的“全知识”状态会展开为显式 EMC 映射知识，以便两个终端完整显示物品，并允许在转化桌逐项遗忘。该操作会增加玩家保存的知识条目数量。
- 默认拒绝带 NBT 的物品；可在元件或接口上切换策略。
- 学会有正 EMC 价值的装液桶后，对应流体以 mB 显示；同一流体有多个已学会桶时，按最低正 EMC 价值计费，每 1000mB 按一个桶的价值收费。
- 流体按整桶预付并缓存，终端和网络设备可以按 1mB 分次提取。剩余已付费流体跨 AE2/RS 共用并保存在世界数据中；遗忘桶知识后仍可取完缓存。
- 流体仅支持由桶表示的类型，当前只支持从 EMC 流体空间取出，不接受流体存入。

## 配置

配置文件位于 `config/emcstoragebridge-common.toml`：

```toml
[display]
refreshRoundTicks = 200
refreshIntervalTicks = 0
maxDisplayAmount = 2147483647

[fluid]
enabled = true

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
