# 太空探索与采矿系统完整实现规格

本稿合并最初的“太空探索与采矿系统详细设计”与随后逐轮确认的修改。优先级：用户最新明确选择 > 核心玩法 > 本稿约定 > 最初AI生成的JSON示例。本文同时描述完整系统目标与分阶段实施边界，不把后续目标写成已实现功能。

## 1. 核心目标与设备职责

太空电梯用于终极稳定采矿，以任务、飞行器与永久据点建立进度，不沿用NH式单纯机器配方挖矿。配置主要由数据包驱动，视觉事件表现测绘、发射、航行与建设的成就感。

- 主机：管理测绘、登陆、永久据点三种开拓任务；不是采矿执行器。
- 采矿模块：选择一个天体、最多三种资源，满足输入与运行条件后自动循环采矿。
- 组装模块：保持现有设计。
- 继续使用TechReborn/RebornCore多方块、能量、库存、精细槽位IO、红石控制与全息图，不另起独立机器框架。
- 一座主机最多绑定模块的限制沿用现有实现（当前8个）；本规格不增加采矿任务的全局互斥。

## 2. 航天器与注油

每种器材有T1/T2/T3三个等级：太空无人机space_drone_mkN、测绘探测器space_probe_mkN、登陆舱体space_lander_mkN、据点核心core_mkN。

无人机、探测器、登陆舱各有独立的未充油物品和`_powered`充油物品；核心不充油。材质路径为assets/techreborn/textures/item/spacecraft/；充油物品模型layer0使用对应原图，layer1叠加通用`_powered.png`。

注油在精密组装机完成，固定512 EU/t、100 tick（1A HV、5秒）。燃料物品、流体单元以及返还空单元完全由整合包配方定义，Java不硬编码油品。设计示例：

| 等级 | 充油示例投入 |
|---|---|
| T1 | 未充油器材+4单元RP-1，或2单元甲烷液氧 |
| T2 | 未充油器材+4单元硝酸肼-四氧化二氮，或2单元偏二甲肼-四氧化二氮 |
| T3 | 未充油器材+10单元液氢液氧+1空间翘曲器 |

器材与返回物同样全部写在任务方案的inputs/outputs中；系统不自动扣取、追加或替换飞行器。`consume`字段删除。

## 3. 队伍进度与阶段

进度以FTB Teams party为作用域，没有party或没有FTB Teams时以玩家UUID为作用域。复用TeamScopes；进度在主世界PersistentState持久化，同队所有电梯共享。

天体阶段按顺序：UNKNOWN(0) -> SURVEYED(1) -> LANDED(2) -> OUTPOST(3)。正常玩法只允许完成下一阶段，不能重复收取开拓奖励。

1. 测绘：投入适用方案的充油探测器及其他材料；完成后解锁详细物理数据与按visibility约定公开的内容。
2. 登陆：先测绘，再投入适用方案登陆舱及材料；完成后解锁长程采矿。
3. 永久据点：先登陆，消耗适用方案的登陆舱、据点核心和大量一次性材料；完成后取消采矿debuff。

恒星仅展示，全部任务关闭。黑洞不可登陆、不可建据点；测绘后可以轨道采矿，永久使用长程模式。其他不需要登陆的轨道采矿也显式配置requires_landing=false，但仍需先测绘。

机器放置时固定记录放置者当时的作用域。旧存档没有归属的机器第一次由玩家打开时认领。之后队伍变化不自动搬迁机器和旧进度；正常玩家不能在打开或发包时重绑归属。其他队伍可浏览公开目录，但不能修改本机任务目标、方案和资源选择。管理员恢复机制应独立于普通玩法。

## 4. 并发、供电与加载

- 每台主机一次只执行一项开拓；同队不同主机可同时开拓不同天体，同队不能对同一天体同时开拓。不同队伍彼此不阻塞。
- 每个采矿模块一次一轮；任意数量模块可同时采同一天体，没有天体采矿锁、没有全局队列。
- 主机开拓不阻塞其采矿模块或组装模块。
- T1/T2/T3设备方案分别使用512/2048/8192 EU/t，即GT式1A HV/EV/IV。单位始终EU/t，不是EU/s。主机开拓不再叠加旧2048 EU/t基础耗电；矿机独立使用自身缓冲/电池供电。主机是否可用按主世界、结构与红石控制决定，组装模块继续自身配方耗电。
- 多任务耗电相加；共享供电调度公平轮转。只有足额扣除本任务本tick所需能量才推进；没轮到或缺电就暂停，进度保留，不预付整次任务电量。
- 任务不吃通用超频，不能通过标准升级叠加额外任务加速。
- 运行时自带必要区块强加载，覆盖执行机器和其结构；远方发电站是否加载由玩家解决。队伍离线仍运行，缺电暂停并保留任务加载票；任务完成、主动暂停或拆机时释放票。已完成堵输出不持续耗电，不需要以任务名义保留加载票。
- 不补算服务器停机期间的现实时间，按有效游戏tick推进。

## 5. 时间与产出公式

设天体任务等级为T，所选方案设备等级为D，D>=T，等级差delta=D-T。允许跨两级。

- 跨级系数：0.25^delta，开拓和采矿都适用。
- 开拓实际tick=max(1,floor(阶段time * 0.25^delta))。
- 有据点采矿tick=max(1,floor(顶层time * 0.25^delta))。
- 无据点采矿tick=max(1,floor(顶层time * 5 * 0.25^delta))。
- 有据点每种资源产量=count；无据点产量=floor(count*0.2)，使用整数除5避免浮点误差。
- 方案outputs中的设备返回物和副产物不受矿石0.2倍率影响；资源表resources才受倍率影响。
- 所选的每一种资源每轮各产一次，不随机抽取、不按权重抽奖。
- `min/max`、`selectable`、`weight`与所有可配置阶段倍率删除；最多三种产出为统一规则。
- 基础count<5的资源在长程模式产出0个，这是确认采用逐轮向下取整的结果，不保存小数余量。

月球基准示例：测绘6000 tick、登陆6000 tick、据点24000 tick；有据点同级采矿1200 tick。T1/T2/T3无人机有据点分别60s/15s/3.75s；T3执行后返回T3，最早示例中的“T3返回T2”按笔误修正。

## 6. 任务生命周期与物品结算

任务状态建议为IDLE、RUNNING、PAUSED_POWER、PAUSED_REDSTONE、WAITING_OUTPUT、CANCELLED。实现应保留唯一任务ID与可幂等结算标记。

启动时服务端校验机器归属、结构、前置阶段、开拓互斥、设备方案、输入、输出承载条件和有效资源选择；一次性从库存扣取并托管所有inputs，同时冻结任务作用域、天体ID、方案等级、输入、输出、功率、耗时和实际矿石产出快照。组件输入表示至少满足指定组件，输出精确携带所配置组件。

任务完成时立即更新队伍阶段并停止耗电；结果进入真实输出库存，放不下则持久化等待交付，阻止该机器下一轮启动。无论重启、重载还是重复打开GUI都不能重复解锁或领取。拆除执行机器取消未完成任务且损失投入，已完成的待交付物按机器库存处理，不作为在途投入销毁。

### /reload最终规则

用户后续明确允许简化为：成功重载时强制取消全部未完成太空任务，原样返还全部托管inputs（包含充油设备和全部组件），已耗电不返、不生成任务outputs、不解锁阶段。已完成的待交付输出保持不变。退款仅保存在原机器的持久化退款状态，尝试放回原输入槽；塞不下则等待输入槽腾空继续退回，不创建队伍退款队列、不掉地上、不静默丢失或重复领取。退款未完成前禁启下一轮。正常重启恢复任务，不按reload取消；仅成功的数据包reload中断未完成任务并关闭自动循环。

退款与取消由任务执行层在新目录发布前完成状态转换，并用持久化reload纪元补偿暂未加载的旧任务。加载新目录前必须先完整解析校验候选数据；失败保留旧目录与revision，不能发布半个星系，也不应因坏包触发退款。已解锁进度不因reload清空。已选天体/资源缺失时保留存档意图但禁用并提示，或由后续明确的配置修复规则处理，绝不能按数组下标挪到别的矿种。

## 7. 数据包格式

路径：data/<namespace>/techreborn/planet/<path>.json，天体ID直接由namespace:path生成，不重复顶层id。例data/sol/techreborn/planet/mars.json -> sol:mars。后加载数据包按同一文件ID覆盖。

### 顶层

| 字段 | 类型/默认 | 含义 |
|---|---|---|
| type | 枚举字符串 | barren/molten/toxic/continental/ocean/frozen/gas_giant/asteroid_belt/black_hole/star |
| tier | 1..3 | 天体任务等级 |
| system | string | 星系分组/筛选；不决定轨道父子 |
| from_system | identifier | 所属恒星/根天体文本，黑洞可指自身 |
| parent | identifier或null | 唯一轨道中心依据；根天体null |
| sort_order | int，稳定ID派生 | 列表顺序 |
| display | 对象 | name_key、description_key |
| marker_block | 已注册方块ID | default blockstate，只读取顶面材质 |
| marker_scale | 正有限数，1.0 | 视觉缩放 |
| time | 正整数tick | 同级有据点基础采矿时间 |
| physical | 对象 | 下述物理数据 |
| missions | 对象 | survey/land/outpost/mining |
| resources | 数组 | 可采固定物品表；采矿启用必须非空 |
| visibility | 对象，名称always/物理after_survey/资源after_survey | 文本公开条件 |
| dimension | 可选ID | 任务背景描述，不加载或进入该维度 |
| tags | 字符串数组 | 筛选用标签，不决定轨道 |

### physical

radius_km、orbit_radius_au、orbit_phase_deg、temperature_k、rotation_period_hours、orbital_period_days均为有限数。非根orbit_radius_au>0，根可省略或为0。orbit_phase_deg可省略，由排序/ID派生稳定相位。orbit_features为字符串数组，每个字符串拼接对应轨道特征翻译键，不再支持任意value对象。

### 阶段与分级方案

survey/land/outpost为`{enabled,time,variants}`；time直接写该阶段同级基础tick，不再写time_multiplier。禁用阶段可省略time/variants。

mining为`{enabled,requires_landing,variants}`；基础time取顶层。每个variant为`{tier,inputs,outputs}`，tier同时决定功率和跨级耗时。一个阶段同一tier只能有一个方案。要支持T3挖T1就显式添加T3方案，并准确写充油T3投入与未充油T3返回。系统不自动生成高级配方。

物品栈写`{item,count,components?}`，count为正整数，组件使用Minecraft1.21.1注册组件格式；输入中声明的组件是匹配约束。survey/land/outpost每组inputs/outputs各最多9个条目；mining每组最多4个inputs、6个outputs。采矿资源另计，返回物和选定资源合并后必须能放进真实6个输出槽。启动时按真实堆叠上限模拟当前输出空间，不够则不扣料；运行时输出被占满则持久化等待交付。

resources条目为`{id,item,count,components?,order?}`。id为本天体内唯一稳定路径（如iron），不得依赖数组位置或可变翻译名。所有条目都可选，每轮固定count。资源排序先order后id。选择按`天体ID+资源ID`持久化。

### 示例骨架

```json
{
  "type": "barren", "tier": 1, "system": "sol",
  "from_system": "sol:sun", "parent": "sol:sun", "sort_order": 30,
  "display": {"name_key": "planet.sol.mars", "description_key": "planet.sol.mars.desc"},
  "marker_block": "minecraft:red_sand", "marker_scale": 1.0, "time": 1200,
  "physical": {
    "radius_km": 3389.5, "orbit_radius_au": 1.524, "orbit_phase_deg": 120.0,
    "temperature_k": 210.0, "rotation_period_hours": 24.6, "orbital_period_days": 687.0,
    "orbit_features": ["axial_tilt"]
  },
  "missions": {
    "survey": {"enabled": true, "time": 6000, "variants": [
      {"tier": 1, "inputs": [{"item": "techreborn:space_probe_mk1_powered", "count": 1}],
       "outputs": [{"item": "techreborn:space_probe_mk1", "count": 1}]}
    ]},
    "land": {"enabled": true, "time": 6000, "variants": [
      {"tier": 1, "inputs": [{"item": "techreborn:space_lander_mk1_powered", "count": 1}],
       "outputs": [{"item": "techreborn:space_lander_mk1", "count": 1}]}
    ]},
    "outpost": {"enabled": true, "time": 24000, "variants": [
      {"tier": 1, "inputs": [{"item": "techreborn:space_lander_mk1_powered", "count": 1},
        {"item": "techreborn:core_mk1", "count": 1}],
       "outputs": [{"item": "techreborn:space_lander_mk1", "count": 1}]}
    ]},
    "mining": {"enabled": true, "requires_landing": true, "variants": [
      {"tier": 1, "inputs": [{"item": "techreborn:space_drone_mk1_powered", "count": 1}],
       "outputs": [{"item": "techreborn:space_drone_mk1", "count": 1}]}
    ]}
  },
  "resources": [{"id": "iron", "item": "minecraft:raw_iron", "count": 20, "order": 1,
    "components": {"minecraft:custom_data": {"source": "mars"}}}],
  "visibility": {"name": "always", "physical": "after_survey", "resources": "after_survey"},
  "tags": ["rocky", "inner_system"]
}
```

完整T1/T2/T3方案以实际内置样例为准；上例仅示意T1。最小内置太阳系包括太阳、用于月球轨道中心的展示地球、月球、火星；原版材料和矿石为明确测试配置，不能视作最终整合包配平。

### 校验

拒绝未知物品/方块/组件、AIR物品、不合法count、非有限物理数、重复资源ID/方案等级、低于天体等级的方案、缺失父节点、循环、缺失from_system。outpost依赖land，requires_landing依赖land，正常登陆依赖survey。恒星不能有任务，黑洞不能登陆/据点。当前每目录最多256天体、每体最多256资源作为同步与GUI上限，超限报错。mining方案超4输入/6返回拒绝加载，其他阶段超9/9拒绝加载。不能接受旧min/max/selectable/consume/multiplier并悄悄按新规则运行；应给清晰错误说明。

## 8. 服务器同步与操作安全

目录为服务器权威，客户端不再使用写死预览目录授予状态。打开本机真实ScreenHandler时同步该窗口的天体快照、实际阶段、本机选择、可编辑权限、结构状态、必要方案摘要与拒绝原因。同步携带窗口syncId、机器位置、目录revision和队伍阶段revision；客户端丢弃旧窗口/旧revision结果，退出连接清空缓存，不能跨服务器泄露。

- 未测绘也同步绘图最小字段：ID、system、parent、半径/相位、marker和scale。visibility只限制文本和资源，不改变轨道绘图。
- name=after_survey时只发匿名文本，不发真实名称/描述翻译键。
- physical未解锁不发半径/温度/自转/公转等文本数据；绘图所需轨道半径除外。
- resources未解锁时不发任何资源物品、数量、组件与任务材料配方，客户端不能通过缓存提前窥视。
- 只同步访问者可见数据；其他队伍打开机器不能得到机器所属队伍的私有进度表。
- C2S只允许请求目录、选天体、选已有方案等级、选择可见资源ID；不能提交完成阶段、任意ItemStack、耗时或产量。
- 每次操作校验当前真实窗口syncId、同位置同维度的机器、距离、加载状态、归属、目录revision、结构及选择合法性，并限制请求频率和数量。
- 队伍变化后下一次同步立即撤销不再具有的编辑权限并重算可见内容。
- 原生红石/IO配置包与手工槽位交互同样由服务端归属校验；跨队玩家只读，不能通过Shift、拖拽、快捷键或直接配置包绕过。物理破坏和外部管道自动化仍按现有机器/领地规则，不在此层创建新的领地保护系统。

## 9. GUI与视觉

布局保留已确认版本：主机中间大星图，左侧天体数据/任务按钮/上九进下九出，右侧模块列表；矿机星图与资源池、目标按钮、自动运行入口。星系通过system分页，一页一个星系，不是把轨道切成纸页。拖动平移、滚轮缩放、点击平滑聚焦跟随；小天体可用列表切换找回。

轨道父子唯一依赖parent；半径直接来自AU，经统一对数映射/屏幕缩放；相位来自定义或稳定默认值。默认画面间距相较首版放大4倍，允许缩小到全景，避免行星挤在一起。marker只使用默认状态顶面。卫星绕行星，小行星带环绕恒星由多个marker组成，大伤口为无恒星的黑洞页。

结构未成型时在机器内容区域覆盖半透明灰层并保留结构提示/全息按钮可用；不灰掉玩家物品栏和原生配置页，不重排已确认布局。目录浏览不等于允许提交机器操作。

任务按钮应显示真实禁用原因：未测绘、未登陆、等级不足、输入不足、任务进行中、输出满、结构未成型、无权限、配置失效以及执行层未接通。数据层阶段禁止伪装成可发射；不允许用客户端阶段按钮解锁。

视觉目标包括扫描波、发射动画、航迹、进度环、粒子/轻微震动、轨道线与迷雾。当前程序背景可保留；GLSL背景/迷雾为增强项目，不是服务端数据层的前置。

## 10. 天体范围

| 等级 | 天体 |
|---|---|
| T1 | 水星、金星、月球、火星、小行星带 |
| T2 | 木星、土星、天王星、海王星、土卫二、海卫一、木卫一、土卫六、土卫八、天卫一 |
| T3 | 罗斯128-b/ba、织女一-b、格利泽581-g、鲸鱼座T星-E、迈罕小行星带、大伤口 |

恒星包括太阳、罗斯128、织女一、格利泽581、鲸鱼座T星、阿蒙-拉；展示星球/背景dimension不带来实际维度传送。格利泽581-g以永恒星光为美术背景，迈罕带以末地为美术背景。具体marker和产物由后续整合包数据定义。

## 11. EMI

开拓页按天体聚合：一个天体一张EMI配方页，页内可见阶段用标签切换、T1/T2/T3方案并列展示各自最多9种inputs、实际返回物、耗时和功率；不同器材等级是替代方案，不累加消耗。未解锁的后续阶段材料不披露。采矿页：方案inputs+目标marker -> 可选资源与方案outputs，明确长程/据点以及跨级规则；测绘且资源可见后可查阅，未登陆只提示执行前置；没有可见方案时保留不含隐藏资源的分类引导页。注油用精密组装机正常配方页。展示marker是条件提示，不是额外消耗物。

## 12. 本轮实施与验收边界

本轮实现数据包解析校验、最小太阳系样例、队伍进度持久化、机器归属和选项持久化、按可见性同步、服务端目标/矿种配置校验、现有UI接入，以及结构未成型灰层。操作员测试命令可推进阶段以验证过滤，但普通GUI没有授予阶段接口。

任务执行层现已接入主机开拓、矿机自动循环、实际组件输入托管、功率/红石暂停、任务区块票、同队同体开拓互斥、输出待交付、原机reload退款。UI提供启动、自动循环开关和任务手动暂停/恢复；关闭自动循环只阻止下一轮，当前轮仍完成。强加载由持久化位置索引在重启时恢复，投入与待交付物只在机器区块内保存，避免跨存档副本重领；阶段完成回执用于恢复阶段写入。下一层兼容与轻量视觉现已实现EMI、Jade、完整器材投入预览、独立模块登记和事件驱动反馈；具体实机验收见兼容层说明。注油整合包配方、正式经济与完整星系内容不由Java层Agent负责。验证覆盖坏数据/父子循环/组件、越权和过期窗口、物品结算、重载退款和存档恢复；独立检查不能替代实际Minecraft多方块/加载票/FTB联机验证。
