# Minecraft IoT (Sensor Display Mod)

Minecraft 1.7.10 Forge 模组：传感器、显示器、执行器与信号域（技能0）组成的"机器物联网"。
贴方块采样 NBT → 表达式格式化 → 多方块大屏显示 / 红石输出 / 值信号远程传递，并支持
跨 MOD（Thermal Expansion、IC2、Railcraft、BuildCraft、GregTech 等）机器的数据读取。

## 功能特性

- **传感器（Sensor）**：贴目标方块放置，六方向采样目标 NBT，自动提取变量；
  绑定卡（Connector）记录传感器坐标与朝向，插卡后链路即建立。
- **显示器（Display）**：多方块拼接成完整屏幕（结构完整性校验，主控方块带状态指示灯）；
  支持多绑定卡、字号/对齐/文字方向/分栏、溢出行为（截断/翻页/滚动）、
  表达式模板渲染（友好格式）与原始 NBT 切换。
- **执行器（Actuator）**：
  - **红石技能**：条件表达式成立时从指定面输出红石信号（强度可调）；
  - **技能0 信号域**（默认技能，无需插件）：条件表达式成立即输出一个值，
    也可写输出表达式自定义返回值；内置 `{in}` 输入变量引用上游传来的信号值；
    结果可指定输出方向，通过三种介质传播：
    1. **紧贴一跳**：相邻执行器直接读取（每 2 tick / 红石刻求值）
    2. **信号总线**：方向直通中继方块，可串联延长
    3. **无线绑卡**：传感器 + 绑定卡绑定远端执行器，读取其 NBT 主键 `SignalOut`
- **连接器工作台（Connector Workbench）**：编辑绑定卡上的显示模板（变量选择、
  自定义表达式）、清空为原始 NBT、设置默认友好显示、拷贝配置/拷贝全部、
  重命名绑定卡。
- **多方块机器解析**：Railcraft 蒸汽锅炉聚合、GregTech 大型机械主控解析
  （下东南定律）、IC2 核反应堆、RC 蓄水器、流体罐聚合等。
- **远程 GUI**：显示器内直接打开绑定目标机器的 GUI 操作（Esc 返回）。
- **语言**：简体中文 / English。

## 环境要求

- JDK 8（1.8.0_202 验证通过）
- Gradle Wrapper 随仓库提供（首次构建自动下载依赖，需联网；
  仓库已配置国内阿里云镜像加速）

## 构建

```bash
# 编译
gradlew build

# 产出可发布 jar（reobf 混淆后输出到 build/release/modid-1.0.jar）
gradlew release
```

> 注意：本仓库使用的 ForgeGradle 分支（`com.anatawa12.forge:ForgeGradle:1.2-1.1.+`）
> 的 `build` 任务不自动执行 reobf，发布请统一使用 `gradlew release`。

## 第三方依赖（需自行准备）

本仓库**不含**以下反混淆（SRG→MCP）第三方 MOD jar（版权原因不随源码分发）。
请将对应文件放入项目根目录 `libs_deobf/` 后即可编译（`compile files` 引用）：

| 文件 | 来源 MOD |
|---|---|
| `CoFHCore-[1.7.10]3.1.4-329-deobf.jar` | CoFH Core |
| `ThermalFoundation-[1.7.10]1.2.6-118-deobf.jar` | Thermal Foundation |
| `ThermalExpansion-[1.7.10]4.1.5-248-deobf.jar` | Thermal Expansion |
| `CodeChickenLib-1.7.10-1.1.3.140-deobf.jar` | CodeChickenLib |
| `CodeChickenCore-1.7.10-1.0.4.35-deobf.jar` | CodeChickenCore |
| `NotEnoughItems-1.7.10-1.0.4.94-deobf.jar` | NotEnoughItems (NEI) |
| `industrialcraft-2-2.2.828a-experimental-deobf.jar` | IndustrialCraft 2 (实验版) |
| `Railcraft-9.12.2.1-deobf.jar` | Railcraft |
| `buildcraft-7.1.23-deobf.jar` | BuildCraft |
| `gregtech-5.09.31-deobf.jar` | GregTech 5 |
| `Waila-1.5.9_1.7.10-deobf.jar` | Waila |
| `Wawla-1.1.1_1.7.10-deobf.jar` | Wawla |
| `NotEnoughCharacters-1.7.10-1.0-deobf.jar` | Not Enough Characters |

各 MOD 的正式发布版可在其官方渠道下载后自行反混淆（例如使用
SpecialSource / MCP 工具链按 SRG→MCP 映射处理）。运行时亦需对应 MOD 本体
（整合包中加载本 MOD 前请先安装上述 MOD）。

## 开发与测试

- `gradlew runClient` 启动开发客户端（运行目录为 `run/`，测试 MOD jar 放置于
  `run/mods/`）；
- 快速测试建议使用包含上述科技 MOD 的整合包环境。

## 许可

本 MOD 源码基于 Minecraft Forge 开发（Minecraft Forge 许可见仓库内
`MinecraftForge-License.txt`），MOD 本体代码与贴图资源版权归作者所有。
