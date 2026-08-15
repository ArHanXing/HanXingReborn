# 添加新多方块机器的完整流程

> 以 `large_ore_crusher`（大型碎岩机）为实际范例整理。所有路径基于项目根目录 `D:\Programming\HanXingReborn`。
> 机器 id（下文用 `<id>` 表示）贯穿始终：结构文件、配方类型、方块实体、EMI、语言键都以此为准。

---

## 0. 多方块结构与配方逻辑

### 0.1 机器本体注册（前置）

1. **配方类型** — `src/main/java/techreborn/init/ModRecipes.java`
   ```java
   public static final RecipeType<RebornRecipe> XXX = RecipeManager.newRecipeType(Identifier.of("techreborn:<id>"));
   ```
2. **配方 JSON** — `src/main/resources/data/techreborn/recipe/<id>/<name>.json`
   ```json
   {
     "type": "techreborn:<id>",
     "power": 128,
     "time": 200,
     "ingredients": [{ "item": "minecraft:cobblestone" }],
     "results": [{ "item": "minecraft:stone", "count": 16 }]
   }
   ```
3. **方块注册** — `src/main/java/techreborn/init/TRContent.java` `Machine` 枚举
   - 普通机器：`XXX(new GenericMachineBlock(GuiType.XXX, XxxBlockEntity::new))`
   - 需要特殊方块行为（如大型机器的开/关状态方块）：自定义 `LargeXxxBlock`
4. **GUI 类型** — `src/main/java/techreborn/blockentity/GuiType.java` 与 `src/client/java/techreborn/client/ClientGuiType.java` 各加一个条目
5. **方块实体类型** — `src/main/java/techreborn/init/TRBlockEntities.java`
   ```java
   public static final BlockEntityType<XxxBlockEntity> XXX =
       register(XxxBlockEntity::new, "<id>", TRContent.Machine.XXX);
   ```
6. **模型资源**（详见下方"模型材质"小节）：
   - `blockstates/<id>.json`（含 active 属性切换 `_on` 模型）
   - `models/block/machines/tierX_machines/<id>.json` 与 `<id>_on.json`
   - `models/item/<id>.json`
   - 纹理文件 `textures/block/machines/tierX_machines/...`
7. **配置项**（可选）— `src/main/java/techreborn/config/TechRebornConfig.java` 增加 `maxInput` / `maxEnergy` 等字段
8. **语言键** — `src/main/resources/assets/techreborn/lang/zh_cn.json` / `en_us.json`：
   - `block.techreborn.<id>`
   - `emi.category.techreborn.<id>`（见第 2 节）

#### 模型材质规范（多方块机器 front 面）
- 大型研磨机、大型压缩机、大型线材轧机 → front 用 `techreborn:block/machines/tier2_machines/multiblock_off|multiblock_on`
- 超级熔炉（furnace_pro_max）→ front 用 `techreborn:block/machines/tier1_machines/multiblock_off|multiblock_on`
- 其余面保留原聚爆压缩机风格材质（`machine_top` / `machine_bottom` / `implosion_compressor_west|east` / `machine_back`）

### 0.2 多方块结构 JSON

**文件**：`src/main/resources/assets/techreborn/multiblock/<id>.json`

```json
{
	"translate": [1, 0, -1],
	"layers": [
		["AAAAA", "AB BA", "AAAAA"],
		["ABABA", "BCBDB", "ABABA"],
		["AAAAA", "ABABA", "AAAAA"]
	],
	"keys": {
		"A": { "block": "techreborn:basic_machine_casing" },
		"B": { "block": "techreborn:advanced_machine_frame" },
		"C": { "block": "minecraft:water" },
		"D": { "block": "minecraft:lava" }
	}
}
```

- `translate`：`[x, y, z]`，图案原点相对控制器方块的偏移
- `layers`：**从底到顶**的层数组；每层是若干等长字符串——`y = 层索引`、`z = 行索引`、`x = 行内字符索引`；**空格 `' '` 跳过**（不验证、不渲染）
- `keys`：字符 → 键定义，支持：
  - `"block": "modid:block_id"` 单方块（按类型匹配，支持水/岩浆等流体方块）
  - `"blocks": ["modid:a", "modid:b"]` 列表任一
  - `"tag": "modid:tag_id"` 方块标签
  - `"not": "modid:block_id"` 非此方块
  - `"air": true` / `"any": true`
  - `"hologram": "modid:block_id"` 可选的全息图方块覆盖

**注册进加载器**：`src/main/java/techreborn/multiblock/MultiblockDefinitionLoader.java` 的 `DEFAULT_DEFINITIONS` 数组加入 `<id>`。用户可在 `run/config/techreborn/multiblock/<id>.json` 放同名文件覆盖默认结构（加载优先级：config 目录 > jar 内置）。

#### ⚠️ 结构"歪掉"的踩坑（translate 数学）
- 验证与全息渲染都执行 `writeMultiblock(writer.rotate(getFacing().getOpposite()))`：结构坐标会按朝向整体旋转（`rotate` 为 `(x,y,z)→(-z,y,x)`，NORTH 转 3 次、WEST 2 次、SOUTH 1 次、EAST 0 次）
- **translate 必须保证旋转后结构围绕控制器对称**，否则结构会歪在机器一侧
- **控制器位置 `(0,0,0)` 映射到的图案字符必须是空格**（或图案外越界），否则永远验证失败——例如 translate `[1,0,-1]` 时控制器落在图案 `(1,0,1)` 处，该字符必须留空
- 修改 translate 后，同层中心字符需同步调整（如 `"ABABA"` → `"AB BA"`）

### 0.3 方块实体与配方逻辑

**文件**：`src/main/java/techreborn/blockentity/machine/multiblock/XxxBlockEntity.java`

```java
public class LargeXxxBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

    public LargeXxxBlockEntity(BlockPos pos, BlockState state) {
        super(TRBlockEntities.XXX, pos, state, "LargeXxx",
                TechRebornConfig.xxxMaxInput, TechRebornConfig.xxxMaxEnergy,
                TRContent.Machine.XXX.block, 2);
        final int[] inputs = new int[]{0};
        final int[] outputs = new int[]{1};
        this.inventory = new RebornInventory<>(3, "LargeXxxBlockEntity", 64, this);
        // crafter 选择见下
        this.crafter.setMaxParallel(N); // 并行上限
    }

    @Override
    public String getMultiblockId() { return "<id>"; }

    @Override
    public boolean canCraft(RebornRecipe rebornRecipe) { return isMultiblockValid(); }

    @Override
    public BuiltScreenHandler createScreenHandler(int syncID, PlayerEntity player) { ... }
}
```

要点：
- 继承 `JsonMultiblockMachineBlockEntity`（JSON 结构驱动基类）：`isMultiblockValid()` 自动查 `MultiblockDefinitionLoader`，带 20 tick 缓存 + 结构包围盒失效追踪
- `getMultiblockId()` 返回结构 id（与结构 JSON 文件名一致）
- `canCraft()` 返回 `isMultiblockValid()` —— 结构无效时拒绝配方
- GUI 布局通过 `createScreenHandler` 定义槽位，客户端用 `GuiLargeMachine`（通用多方块 GUI，自动绘制槽背景、进度条、能量条、全息图按钮）

#### crafter 三种模式（配方逻辑）

| 模式 | 适用场景 | 写法 |
|---|---|---|
| 独立配方 | 机器有自己的 RecipeType 与配方 JSON | `new RecipeCrafter(ModRecipes.XXX, this, in, out, inventory, inputs, outputs)` |
| 代理配方 | 大型机跑小机器配方（不重复写配方） | 继承 `ProxyRecipeCrafter`：`super(ModRecipes.LARGE_XXX, List.of(ModRecipes.SMALL_XXX), 0.5F, 0.8F, ...)` —— 时间 ×0.5、功率 ×0.8 |
| 特殊逻辑 | 热量加速/并行/输入不消耗等 | 覆写 crafter 方法（见下） |

常用覆写点（参考 `src/main/java/techreborn/recipe/`）：
- `updateCurrentRecipe()` — 重算耗时：EBF 热量每高出配方需求 1000HU，耗时 ×0.8（`BlastFurnaceRecipeCrafter`：`HEAT_STEP=1000`、`TIME_FACTOR_PER_TIER=0.8`，公式 `baseNeededTicks * 0.8^tiers`）
- `getParallelCount(RebornRecipe)` — 并行数：RHF 基础并行 4，热量每高出 1000HU 并行 ×4（`RhfRecipeCrafter`：`BASE_PARALLEL=4`、`HEAT_PARALLEL_MULTIPLIER=4`），仍受输入/输出槽限制
- `useAllInputs()` — 置空实现"输入不消耗"（`OreCrusherRecipeCrafter`：碎岩机输入槽物品永不消耗）

---

## 1. 全息图注册（⚠️ 极其重要，最易遗漏）

**文件**：`src/client/java/techreborn/TechRebornClient.java`（`onInitializeClient` 内，L210-228 一带）

```java
BlockEntityRendererFactories.register(TRBlockEntities.XXX, MultiblockRenderer::new);
```

- 每一个 JSON 多方块机器都必须注册 `MultiblockRenderer`（`reborncore.client.multiblock.MultiblockRenderer`），否则游戏内**完全不显示全息图**
- 该渲染器在方块实体 `renderMultiblock = true` 时渲染 `writeMultiblock` 输出的结构全息图
- **历史教训**：`LARGE_ORE_CRUSHER` 曾漏注册，结果只有 EMI 信息页与结构验证正常、世界内全息图缺失

**GUI 全息图按钮**（`src/client/java/techreborn/client/gui/GuiLargeMachine.java`，多方块机器共用）：
- 结构有效：`drawHologramButton(6, 4)` + `addHologramButton(6, 4, 212)`（右上角小按钮）
- 结构无效：`drawMultiblockMissingBar`（缺结构警示条）+ `addHologramButton(76, 56, 212)`
- 点击回调：`blockEntity.renderMultiblock ^= !hideGuiElements();` 切换全息显示

---

## 2. EMI 条目注册

### 2.1 独立配方（有专属 RecipeType）→ 完整 Category + EMITexture 略缩图

**文件**：`src/client/java/techreborn/client/compat/emi/`

1. **略缩图位置** — `core/EmiTextures.java`：
   ```java
   public static final EmiTexture XXX = new EmiTexture(SIMPLIFIED_ICONS, u, v, 16, 16);
   ```
   - `SIMPLIFIED_ICONS = techreborn:textures/gui/simplified_icons.png`（16×16 网格图集）
   - `(u, v)` 是图集中的**像素坐标**（每格 16px，例如 `GRINDING` 在 `(0,0)`、`ALLOY_SMELTING` 在 `(16,0)`）
   - **没有专属图标时直接复用现有略缩图**（碎岩机即复用 `GRINDING (0,0)`）；新增图标需先在 `simplified_icons.png` 里预留 16×16 格子

2. **注册 Category 与配方** — `TREmiPlugin.java`：
   ```java
   public static final EmiStack XXX_STACK = EmiStack.of(TRContent.Machine.XXX);
   public static final EmiRecipeCategory XXX_CATEGORY = new EmiRecipeCategory(
       trId("<id>"), XXX_STACK, EmiTextures.XXX, EmiRecipeSorting.compareOutputThenInput());
   ```
   `register()` 内：
   ```java
   registry.addCategory(XXX_CATEGORY);
   registry.addWorkstation(XXX_CATEGORY, XXX_STACK);
   for (var recipe : getRecipes(registry, ModRecipes.XXX)) {
       registry.addRecipe(new XxxEmiRecipe(recipe));
   }
   ```

3. **配方展示布局** — 新建 `XxxEmiRecipe.java`，继承 `TREmiRecipe<RebornRecipe>`：
   - `getCategory()` 返回上面的 Category
   - `getDisplayWidth()/getDisplayHeight()` 定义面板尺寸（碎岩机为 `100×72`）
   - `addWidgets()` 摆放槽位与进度/能量部件：
     ```java
     widgets.addSlot(getInput(0), x, y).recipeContext(this);
     widgets.addSlot(getOutput(0), x, y).large(true).recipeContext(this);
     TRUIUtils.energyBar(widgets, recipe, x, y, h);
     TRUIUtils.arrowRight(widgets, recipe, x, y);
     ```
   - 参考模板：`OreCrusherEmiRecipe.java`（工业研磨机略缩图风格）、`SimpleOneInputEmiRecipe.java`

4. **多方块信息页** — `TREmiPlugin.java` 的 `MULTIBLOCK_MACHINES` map 加入：
   ```java
   Map.entry("<id>", TRContent.Machine.XXX)
   ```
   自动生成 `multiblock_info` 类别条目（列出结构所需方块 × 数量 → 控制器方块），并自动包含 config 自定义结构

5. **语言键**：`emi.category.techreborn.<id>`（zh_cn/en_us）

### 2.2 代理配方（复用其他机器配方）→ 仅 Workstation 注册

若大型机只是代理小机器配方（无独立 RecipeType），**不需要新 Category**，直接挂在现有类别下：

```java
registry.addWorkstation(EXISTING_CATEGORY, LARGE_XXX_STACK);
```

范例（TREmiPlugin 现有代码）：
- `COMPRESSOR_CATEGORY` + `LARGE_COMPRESSOR_STACK`（大型压缩机）
- `WIRE_MILL_CATEGORY` + `LARGE_WIRE_MILL_STACK`（大型线材轧机）
- `GRINDER_CATEGORY` + `LARGE_GRINDER_STACK`（大型研磨机）
- `ORE_CRUSHER_CATEGORY` + `LARGE_ORE_CRUSHER_STACK`（大型碎岩机，同时有独立配方，两者并存）
- `BLAST_FURNACE_CATEGORY` + `ROTARY_HEARTH_FURNACE_STACK`（RHF 代理 EBF 配方）
- `CHEMICAL_REACTOR_CATEGORY` + `LARGE_CHEMICAL_REACTOR_STACK`

---

## 3. Tooltip 编写（特殊效果说明）

**机制**：`src/client/java/techreborn/client/events/StackToolTipHandler.java` 在物品 Tooltip 中调用
`ToolTipAssistUtils.addInfo(item.getTranslationKey(), lines)` → 读取语言键 **`techreborn.message.info.block.techreborn.<id>`**（机器物品翻译键 `block.techreborn.<id>` 自动拼接前缀）。

显示规则（`ToolTipAssistUtils.addInfo`）：
- 默认 `hidden=true`：未按 Shift 只显示蓝色提示 `techreborn.tooltip.more_info`（"按住 Shift 查看详情"）
- 按住 Shift：显示完整说明，**金色**，`\n` 分隔多行
- 若方块实体实现了 `IListInfoProvider`，额外走 `addInfo(lines, ...)`

**编写位置**：`zh_cn.json` / `en_us.json`

```jsonc
"techreborn.message.info.block.techreborn.<id>": "描述…\n特殊效果说明…"
```

**特殊效果必须写明的范例**（对应 crafter 实现）：

| 机器 | 效果                            | 对应实现 |
|---|-------------------------------|---|
| EBF（工业高炉） | 线圈热量每高于配方所需 1000K，耗时 ×0.8     | `BlastFurnaceRecipeCrafter`（`0.8^tiers`） |
| RHF（转底炉） | 基础并行 4；热量每高于配方所需 1000K，并行 ×4  | `RhfRecipeCrafter`（`BASE_PARALLEL=4`、`HEAT_PARALLEL_MULTIPLIER=4`） |
| 大型碎岩机 | 0.5× 耗时、0.8× 功率、并行上限 16、输入不消耗 | `ProxyRecipeCrafter(0.5F, 0.8F)` + `setMaxParallel(16)` + `OreCrusherRecipeCrafter` |
| 大型研磨机/压缩机等 | 0.5× 耗时、0.8× 功率、并行上限          | `ProxyRecipeCrafter` + `setMaxParallel` |

---

## 附：完成清单与验证

- [ ] 结构 JSON + `DEFAULT_DEFINITIONS` 注册（含 translate 对称性与控制器空格检查）
- [ ] `ModRecipes` 配方类型 + 配方 JSON + 方块/方块实体/GuiType/配置/模型/语言注册
- [ ] `TechRebornClient` 注册 `MultiblockRenderer`（全息图）✅ 最易遗漏
- [ ] EMI：`EmiTextures` 略缩图、Category/Workstation、`XxxEmiRecipe`、`MULTIBLOCK_MACHINES`、`emi.category` 语言键
- [ ] Tooltip：`techreborn.message.info.block.techreborn.<id>` 说明特殊效果
- [ ] 编译验证：`.\gradlew.bat compileJava compileClientJava`
