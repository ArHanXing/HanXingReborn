少造轮子 少写屎山
如果有部分材质缺失，尽快报告。
如果有一个方法不好实现，可以查询有没有什么轮子可以辅助实现

---
优化数采的GUI，修复文字重叠，找 Bug

---
并行升级：
和原本的超频升级放置位置一样。但是效果是让机器并行数量x4。
和原本的超频升级等抢占槽位。
仅能用于原本就有并行机制的机器上（包括工业高炉）。
需要建立tooltip说明机制。

需要注册新物品。
材质：item/upgrade/parallel_upgrade

---
添加杂项多方块独立机器：
超维度等离子锻炉（DTPF, Dimensionally Transcendent Plasma Forge）：基础4并行，独立机器，不允许并行卡，只能安装硅岩、凯金、三钛线圈，带有和RHF接近的热量机制。基础热量3000K，热量每超过配方1000K，并行x4。GUI和EBF一样。
希望能够自适应结构里的线圈方块。

需要注册主机和其结构方块「超维度机械方块」。
主机侧面材质和维度注入机械方块材质：machines/structure/dimensionally_transcendent_casing
主机正面材质：machines/tier3_machine/dtpf_multiblock_off 和 machines/tier3_machine/dtpf_multiblock_on
