// ===================================================================
// TechReborn 自定义多方块机器开发全流程参考
// 基于 大型化学反应釜 (Large Chemical Reactor) 的实际开发经验
// 项目映射: Mojang Mapping → Yarn Mapping (Fabric)
// ===================================================================

// ===================================================================
// 第1阶段：核心服务端代码
// ===================================================================

// --- 步骤1: 创建 BlockEntity 类 ---
// 位置: src/main/java/techreborn/blockentity/machine/multiblock/
// 参考: ImplosionCompressorBlockEntity.java (最简多方块机器)
//        IndustrialGrinderBlockEntity.java  (含流体的多方块)
//
// 关键点:
//   - 继承 GenericMachineBlockEntity
//   - 构造函数: super(TRBlockEntities.XXX, pos, state, "机器名",
//                    TechRebornConfig.xxxMaxInput, TechRebornConfig.xxxMaxEnergy,
//                    TRContent.Machine.XXX.block, 总槽位数);
//   - inventory: RebornInventory<>(槽位数, "BlockEntityName", 64, this)
//   - crafter: RecipeCrafter(ModRecipes.XXX, this, 输入数, 输出数, inventory, inputs, outputs)
//   - 覆写 writeMultiblock(MultiblockWriter writer) 定义多方块结构
//     writer.translate(1, -1, -1) — 控制器前移1格，下移1格
//     使用 writer.addBlock() / writer.fill() 构建结构

// --- 步骤2: 注册 TRContent.Machine 枚举 ---
// 文件: src/main/java/techreborn/init/TRContent.java
// 在 Machine 枚举中添加条目，格式:
//   XXX(new GenericMachineBlock(GuiType.XXX, XxxBlockEntity::new)),
// import 使用通配符: import techreborn.blockentity.machine.multiblock.*;

// --- 步骤3: 注册 TRBlockEntities ---
// 文件: src/main/java/techreborn/init/TRBlockEntities.java
// 格式:
//   public static final BlockEntityType<XxxBlockEntity> XXX =
//       register(XxxBlockEntity::new, "snake_case_name", TRContent.Machine.XXX);

// --- 步骤4: 注册 ModRecipes 配方类型 ---
// 文件: src/main/java/techreborn/init/ModRecipes.java
// 格式:
//   public static final RecipeType<RebornRecipe> XXX =
//       RecipeManager.newRecipeType(Identifier.of("techreborn:snake_case_name"));

// --- 步骤5: 注册 TechRebornConfig 配置项 ---
// 文件: src/main/java/techreborn/config/TechRebornConfig.java
// 格式:
//   @Config(config = "machines", category = "xxx", key = "XxxMaxInput",
//           comment = "Xxx Max Input (Energy per tick)")
//   public static int xxxMaxInput = 256;
//   @Config(config = "machines", category = "xxx", key = "XxxMaxEnergy",
//           comment = "Xxx Max Energy")
//   public static int xxxMaxEnergy = 40_000;

// ===================================================================
// 第2阶段：GUI 与模型
// ===================================================================

// --- 步骤6: 注册服务端 GuiType ---
// 文件: src/main/java/techreborn/blockentity/GuiType.java
// 添加 import 和条目:
//   import ...XxxBlockEntity;
//   public static final GuiType<XxxBlockEntity> XXX =
//       new GuiType<>((t, player) -> new XxxContainer(t, player));

// --- 步骤7: 注册客户端 ClientGuiType + GUI 类 ---
// 文件: src/client/java/techreborn/client/ClientGuiType.java
//   import ...XxxBlockEntity;
//   import ...GuiXxx;
//   public static final ClientGuiType<XxxBlockEntity> XXX =
//       register(GuiType.XXX, GuiXxx::new);
//
// 创建 GUI 类: src/client/java/techreborn/client/gui/GuiXxx.java
// 参考: GuiImplosionCompressor.java (最简)

// --- 步骤8: 模型文件和纹理 ---
// 需要创建以下文件 (参考 implosion_compressor 复制修改):
//   1. blockstates/<snake_case_name>.json
//   2. models/block/machines/tier2_machines/<snake_case_name>.json (off 状态)
//   3. models/block/machines/tier2_machines/<snake_case_name>_on.json (on 状态)
//   4. models/item/<snake_case_name>.json (指向 off 模型)

// ===================================================================
// 第3阶段：多方块全息图 (常见遗漏!)
// ===================================================================

// --- 步骤9: 注册 MultiblockRenderer ⚠️ 极其重要! ---
// 文件: src/client/java/techreborn/TechRebornClient.java (~L175-L185)
// 每个多方块 BlockEntityType 必须单独注册, 否则全息图不渲染!
//   BlockEntityRendererFactories.register(TRBlockEntities.XXX, MultiblockRenderer::new);
//
// writeMultiblock() 中的 translate(x, y, z):
//   - y = -1: 控制器在结构的顶部 (如 ImplosionCompressor, VacuumFreezer 用 y=-3)
//   - y = -1: 控制器在结构面的中心 (如 IndustrialGrinder, DistillationTower)
//   - 建议参考类似机器的 translate 值和外壳层级

// ===================================================================
// 第4阶段：REI 兼容
// ===================================================================

// --- 步骤10: ReiPlugin 注册 ---
// 文件: src/client/java/techreborn/client/compat/rei/ReiPlugin.java
// 三处修改:
//   a) iconMap 添加: iconMap.put(ModRecipes.XXX, Machine.XXX);
//   b) registry.addWorkstations 添加: .addWorkstation(XXX_CATEGORY, ...)
//   c) registry.add 添加: registry.add(new XxxCategory<>(ModRecipes.XXX));

// --- 步骤11: 创建 REI Category ---
// 文件: src/client/java/techreborn/client/compat/rei/machine/XxxCategory.java
// 参考: ImplosionCompressorCategory.java (最简)
// 自定义输入/输出槽位布局:
//   - 需要覆写 setupDisplay() 来排列槽位
//   - Widgets.createSlot(Point(x, y)) 定义每个槽位
//   - createEnergyDisplay() / createProgressBar() 创建通用控件

// ===================================================================
// 第5阶段：EMI 集成 (完整步骤)
// ===================================================================

// --- 步骤12: build.gradle 配置 ---
// 文件: build.gradle
// Maven 仓库:
//   maven { url "https://maven.terraformersmc.com/releases" }
// 依赖:
//   modCompileOnly "dev.emi:emi-fabric:${emi_version}:api"
//   modLocalRuntime "dev.emi:emi-fabric:${emi_version}"
// gradle.properties:
//   emi_version=1.1.22+1.21.1

// --- 步骤13: 创建 EMI 核心工具类 ---
// 目录: src/client/java/techreborn/client/compat/emi/core/
// 必建文件 (从 ExMI 源码转换, 注意 Mojang→Yarn 映射):
//   LongHolder.java           — 简单的 long 值持有器
//   EmiTextures.java          — 简化图标纹理引用
//   NinePatchTexture.java     — 九宫格纹理渲染
//   NinePatchWidget.java      — 九宫格 EMI Widget
//   UIUtils.java              — 通用 UI 工具 (流体渲染, 数值格式化)
//   ExMILog.java              — Logger
//   FluidFromContainerEmiRecipe.java  — 流体取出配方
//   FluidIntoContainerEmiRecipe.java  — 流体装入配方

// --- 步骤14: 创建 TR 专用工具类 ---
// 目录: src/client/java/techreborn/client/compat/emi/
// 必建文件:
//   TRTextures.java           — TR 纹理引用 (能量条, 箭头, 槽位背景等)
//   TRUIUtils.java            — TR UI 工具 (能量条, 箭头的 widget 构建)
//   FabricFluidSlotWidget.java— 流体槽位 Widget 基类
//   TRFluidSlotWidget.java    — TR 风格流体槽位 Widget
//   FabricUIUtils.java        — Fabric 流体渲染工具
//   TRIntegration.java        — Machine→EmiStack 映射

// --- 步骤15: 创建 EMI Recipe 基类 + 通用展示类 ---
// 文件:
//   TREmiRecipe.java          — 基类, 提取 inputs/outputs/id
//   SimpleOneInputEmiRecipe.java  — 单输入单输出通用展示
//   SimpleTwoInputEmiRecipe.java  — 双输入单输出通用展示

// --- 步骤16: 为所有现有机器创建 EmiRecipe 展示类 ---
// 以下每个文件对应一个机器类型, 需全部创建:
//   AlloySmelterEmiRecipe.java        [SimpleTwoInput]
//   AssemblingMachineEmiRecipe.java   [双输入→单输出自定义]
//   BlastFurnaceEmiRecipe.java        [含 Heat 显示自定义]
//   CentrifugeEmiRecipe.java          [双输入→四输出自定义]
//   ChemicalReactorEmiRecipe.java     [SimpleTwoInput]
//   CompressorEmiRecipe.java          [SimpleOneInput]
//   DistillationTowerEmiRecipe.java   [双输入→多输出, NinePatch]
//   ExtractorEmiRecipe.java           [SimpleOneInput]
//   GrinderEmiRecipe.java             [双→多 SimpleTwoInput 变体]
//   ImplosionCompressorEmiRecipe.java [双输入双行→双输出]
//   IndustrialElectrolyzerEmiRecipe.java [多输入→多输出, NinePatch]
//   IndustrialGrinderEmiRecipe.java   [含流体输入槽]
//   IndustrialSawmillEmiRecipe.java   [含流体输入槽]
//   RecyclingEmiRecipe.java           [Scrapbox]
//   VacuumFreezerEmiRecipe.java       [SimpleOneInput]
//   FluidGeneratorEmiRecipe.java      [流体→电, ⚠️必须保存fluidCapacity]
//   FluidReplicatorEmiRecipe.java     [含流体输出槽]
//   FusionReactorEmiRecipe.java       [含启动能量/最小尺寸文本]
//   RollingMachineEmiRecipe.java      [3×3 合成格]
//   LargeChemicalReactorEmiRecipe.java [6输入→4输出]

// --- 步骤17: 创建 TREmiPlugin 主入口 ---
// 文件: src/client/java/techreborn/client/compat/emi/TREmiPlugin.java
// @EmiEntrypoint 注解
// 实现 EmiPlugin 接口
// 注册所有 EmiRecipeCategory (共 ~26 个)
// 注册所有机器的 WORKSTATIONS 映射
// 添加 Fluid ↔ Cell 容器配方
// 批量注册所有机器的 EmiRecipe

// --- 步骤18: fabric.mod.json 注册 ---
// 文件: src/main/resources/fabric.mod.json
// 在 entrypoints 中添加:
//   "emi": [ "techreborn.client.compat.emi.TREmiPlugin" ]

// --- 步骤19: 复制 EMI 纹理文件 ⚠️ 常见遗漏! ---
// 源文件 (从 ExMI 源码):
//   .exmiSrc/modules/core-xplat/src/main/resources/assets/
//     extra_mod_integrations_core/textures/gui/
//     ├── simplified_icons.png
//     └── widgets.png
//
// 目标路径:
//   src/main/resources/assets/techreborn/textures/gui/
//   ├── emi_simplified_icons.png
//   └── emi_widgets.png
//
// ⚠️ 缺少 emi_widgets.png → 蒸馏塔/工业电解器等输出框渲染为黑底!
// ⚠️ 终端环境可能无法使用 Copy-Item, 需在文件资源管理器中手动复制!

// --- 步骤20: 添加 EMI 翻译键 ⚠️ 常见遗漏! ---
// 文件: lang/en_us.json, lang/zh_cn.json
// 需要两套翻译键:

// A) EMI Category 显示名 (格式: emi.category.<namespace>.<path>)
//    每个 EmiRecipeCategory 都需要, 共 ~26 个
//    示例: "emi.category.techreborn.alloy_smelter": "合金熔炼"

// B) EMI 通用功能翻译键:
//    "gui.techreborn.emi.heat": "温度: %s"
//    "gui.techreborn.emi.start_e": "启动: %s"
//    "gui.techreborn.emi.min_size": "尺寸: %s"
//    "tooltip.techreborn.emi.recipe_power": "能量: %s"
//    "tooltip.techreborn.emi.recipe_power.consumed": "消耗: %s"
//    "tooltip.techreborn.emi.recipe_power.produced": "产出: %s"
//    "tooltip.techreborn.emi.recipe_power_per_bucket": "能量/桶: %s"
//    "gui.extra_mod_integrations_core.cook_time": "%f秒"
//    "metric.format.0" ~ "metric.format.9": 数值格式化后缀

// ===================================================================
// 第6阶段：配方和翻译
// ===================================================================

// --- 步骤21: 创建机器合成配方 ---
// 文件: data/techreborn/recipe/crafting_table/machine/<snake_case_name>.json
// 参考: implosion_compressor.json
// 通常使用 crafting_shaped 格式

// --- 步骤22: 创建处理配方 (至少一个测试配方) ---
// 文件: data/techreborn/recipe/<snake_case_name>/<配方名>.json
// 格式:
//   { "type": "techreborn:<snake_case_name>", "power": 50, "time": 400,
//     "ingredients": [...], "outputs": [...] }

// --- 步骤23: 添加方块翻译键 ---
// 文件: lang/en_us.json, lang/zh_cn.json
// 格式: "block.techreborn.<snake_case_name>": "显示名"

// ===================================================================
// 第7阶段：常见陷阱与 API 映射
// ===================================================================

// ⚠️ 陷阱1: 全息图不渲染
//   原因: 未在 TechRebornClient.java 注册 MultiblockRenderer
//   修复: BlockEntityRendererFactories.register(TRBlockEntities.XXX, MultiblockRenderer::new);

// ⚠️ 陷阱2: 多方块结构偏移错误
//   原因: writeMultiblock() 中 translate() 参数不正确
//   参考: translate(1, -1, -1) — 控制器在结构面前方居中
//         translate(1, -3, -1) — 控制器在结构面前方顶部

// ⚠️ 陷阱3: REI 配方不显示
//   原因: 未在 ReiPlugin.java 注册 iconMap / Category / Workstation
//   三步都要加!

// ⚠️ 陷阱4: 流体发电机 EMI 不渲染流体
//   原因: FluidGeneratorEmiRecipe 构造函数丢弃了 fluidCapacity 参数
//   修复: 保存 fluidCapacity 字段, 容量公式 fluidCapacity * 100 * 81

// ⚠️ 陷阱5: EMI category 名显示为英文 ID
//   原因: 未添加 emi.category.techreborn.* 翻译键
//   修复: 在 lang 文件中手动添加 ~26 个键

// ⚠️ 陷阱6: 蒸馏塔/工业电解器输出框黑底
//   原因: emi_widgets.png 纹理文件缺失
//   修复: 从 ExMI 源码复制 widgets.png → emi_widgets.png

// ⚠️ 陷阱7: 流体渲染透明
//   原因: UIUtils.renderFluid() 缺少 RenderSystem.enableBlend()
//   修复: 在 GameRenderer::getPositionTexColorProgram 前添加 enableBlend()

// ===================================================================
// Mojang → Yarn 关键 API 映射速查表
// ===================================================================

// | Mojang (原ExMI)                | Yarn (本项目的Fabric映射)        |
// |-------------------------------|----------------------------------|
// | ResourceLocation              | Identifier                       |
// | ResourceLocation.fromNamespaceAndPath(ns, p) | Identifier.of(ns, p) |
// | BuiltInRegistries             | Registries                       |
// | net.minecraft.core.registries.BuiltInRegistries | net.minecraft.registry.Registries |
// | net.minecraft.world.level.material.Fluid | net.minecraft.fluid.Fluid |
// | net.minecraft.world.item.crafting.RecipeHolder | net.minecraft.recipe.RecipeEntry |
// | net.minecraft.world.item.crafting.ShapedRecipe | net.minecraft.recipe.ShapedRecipe |
// | Component.translatable(s).getVisualOrderText() | Text.translatable(s).asOrderedText() |
// | ClientTooltipComponent.create() | TooltipComponent.of()         |
// | Mth.clamp()                   | MathHelper.clamp()               |
// | DrawContext.pose()            | DrawContext.getMatrices()         |
// | PoseStack                     | MatrixStack                      |
// | PoseStack.last().pose()       | MatrixStack.peek().getPositionMatrix() |
// | com.mojang.blaze3d.vertex.*   | net.minecraft.client.render.*      |
// | BufferBuilder.addVertex(mat,x,y,z) | BufferBuilder.vertex(mat,x,y,z) |
// | BufferBuilder.buildOrThrow()  | BufferBuilder.end()               |
// | VertexConsumer.addVertex(mat,x,y,z) | VertexConsumer.vertex(mat,x,y,z) |
// | VertexConsumer.setUv(u,v)     | VertexConsumer.texture(u,v)       |
// | BufferUploader.drawWithShader() | BufferRenderer.drawWithGlobalProgram() |
// | DefaultVertexFormat.POSITION_TEX | VertexFormats.POSITION_TEXTURE  |
// | VertexFormat.Mode.QUADS       | VertexFormat.DrawMode.QUADS       |
// | GameRenderer.getPositionTexShader() | GameRenderer.getPositionTexProgram() |
// | TextureAtlasSprite            | Sprite (net.minecraft.client.texture) |
// | Sprite.getU0()/getU1()/getV0()/getV1() | Sprite.getMinU()/getMaxU()/getMinV()/getMaxV() |
// | GuiGraphics                   | DrawContext                      |
// | recipe.getResultItem(null)    | recipe.outputs().getFirst()      |

// ===================================================================
// 完整文件修改清单 (以 LCR 为例, 共 ~50 个文件)
// ===================================================================

// 【核心服务端】 (5 文件)
//  1. LargeChemicalReactorBlockEntity.java    [新建] BlockEntity
//  2. TRContent.java                          [修改] Machine 枚举 +1
//  3. TRBlockEntities.java                    [修改] 类型注册 +1
//  4. ModRecipes.java                         [修改] 配方类型 +1
//  5. TechRebornConfig.java                   [修改] 配置项 +2

// 【GUI 与模型】 (6 文件)
//  6. GuiType.java                            [修改] 服务端 GUI +1
//  7. ClientGuiType.java                      [修改] 客户端 GUI +1
//  8. GuiLargeChemicalReactor.java            [新建] GUI 类
//  9. blockstates/large_chemical_reactor.json [新建] 方块状态
// 10. models/block/.../large_chemical_reactor.json       [新建] 模型 off
// 11. models/block/.../large_chemical_reactor_on.json    [新建] 模型 on
// 12. models/item/large_chemical_reactor.json            [新建] 物品模型

// 【多方块全息图】 (1 文件)
// 13. TechRebornClient.java                   [修改] MultiblockRenderer +1

// 【REI 兼容】 (2 文件)
// 14. ReiPlugin.java                          [修改] iconMap+Category+Workstation
// 15. LargeChemicalReactorCategory.java       [新建] REI 分类

// 【EMI 集成】 (~26 文件)
// 16. build.gradle                             [修改] EMI 依赖
// 17. gradle.properties                        [修改] emi_version
// 18. fabric.mod.json                          [修改] emi entrypoint
// 核心工具类: LongHolder, EmiTextures, NinePatchTexture, NinePatchWidget,
//            UIUtils, ExMILog, FluidFromContainerEmiRecipe, FluidIntoContainerEmiRecipe
// TR 工具类: TRTextures, TRUIUtils, FabricFluidSlotWidget, TRFluidSlotWidget,
//           FabricUIUtils, TRIntegration
// 基类: TREmiRecipe, SimpleOneInputEmiRecipe, SimpleTwoInputEmiRecipe
// 机器展示类 (~18 个): AlloySmelterEmiRecipe, AssemblingMachineEmiRecipe,
//   BlastFurnaceEmiRecipe, CentrifugeEmiRecipe, ChemicalReactorEmiRecipe,
//   CompressorEmiRecipe, DistillationTowerEmiRecipe, ExtractorEmiRecipe,
//   GrinderEmiRecipe, ImplosionCompressorEmiRecipe,
//   IndustrialElectrolyzerEmiRecipe, IndustrialGrinderEmiRecipe,
//   IndustrialSawmillEmiRecipe, RecyclingEmiRecipe, VacuumFreezerEmiRecipe,
//   FluidGeneratorEmiRecipe, FluidReplicatorEmiRecipe,
//   FusionReactorEmiRecipe, RollingMachineEmiRecipe,
//   LargeChemicalReactorEmiRecipe
// 主入口: TREmiPlugin.java

// 【纹理】 (2 文件, 需手动复制)
//   emi_simplified_icons.png — 机器图标
//   emi_widgets.png          — 九宫格背景

// 【翻译】 (2 文件)
//   en_us.json — 机器名 + ~26 EMI category + ~10 通用键
//   zh_cn.json — 同上中文版

// 【配方】 (2+ 文件)
//   machine crafting recipe — 机器合成配方
//   处理配方 × N — 至少一个测试配方

// ===================================================================
// 快速调试清单
// ===================================================================
// [ ] 全息图是否显示?          → TechRebornClient MultiblockRenderer
// [ ] REI 配方是否可见?        → ReiPlugin iconMap+Category+Workstation
// [ ] REI 配方布局是否合理?    → XxxCategory.setupDisplay 槽位坐标
// [ ] EMI 配方是否可见?        → TREmiPlugin 注册了对应 Category
// [ ] EMI category 名称是否汉化? → lang 文件 emi.category.* 键
// [ ] EMI 输出框是否有黑底?    → emi_widgets.png 纹理
// [ ] 流体是否正常渲染?        → enableBlend() / fluidCapacity 保存
// [ ] 翻译键是否全部正常?      → 三方翻译键检查 (block, category, tooltip)
// [ ] 机器合成配方是否可用?    → crafting_table JSON
// [ ] 处理配方是否能执行?      → recipe JSON 格式正确
