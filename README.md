# 使用 Vibe Coding，编码模型为 DeepSeek v4 Flash-0731

![](https://i.imgur.com/m0y21EK.png "Tech Reborn")
# 涵星喵 Reborn
使用 DeepSeek v4 pro的对 Tech Reborn 的二次修改。用于 CmRhPack 整合包。

已经实现：
- 工业高炉可替换线圈，新的热量系统；对于EBF，热量每高于配方1000J，耗时x0.8
- 内置 EMI 兼容
- 多方块现在支持了更复杂、数据驱动的结构，并且追加了缓存优化成型检测
- 代理配方，现在可以支持大化反/工业磨粉运行小化反/小磨粉的配方，且有时间和耗能加成
- 并行系统，目前所有大机器和工业机器均具有16并行
- 多方块的信息页面，现在可以按 + 号直接编写多方块结构的样板
- 多方块搭建器，集结构检测和自动放置为一体
- Jade 集成，更方便地查看配方输入与输出、耗电发电信息
新机器：
- 转底炉，其热量影响并行（基础4并，热量每高于配方1000J，并行x4）
- 独立机器：精密组装机、碎岩机、车床
- 改良大机器：原始蒸馏塔、大型研磨塔、大型线材轧机、大型压缩机、大型化学反应釜、大型碎岩机、大型车床
- 超维度等泥土熔炉...的同分异构体
TODO：
- 更多改良机器：大型合金炉、大型离心机组、大型电解槽、大型提取机、质量发生器、大型温室、珠海渔场
- 更多发电手段：大型燃气涡轮、大型柴油机、通用化学能引擎、硅岩反应堆
- 更多新机制：精密组装兼容装配机、重构聚变堆逻辑、裂变反应堆
- GUI和结构优化，修正歪掉的结构、歪掉的箭头和不合时宜的贴图
- 本模组目前还不能直接添加至现有整合包，多数新物品没有配方
# Tech Reborn

*Tech Reborn is a completely standalone tech mod including tools and machines to gather resources, process materials, and progress through the mod.*

# Downloads

*Remember to always backup your worlds before adding or updating mods!*

### Recommended Releases
Versions we determine are stable enough can always be found on our CurseForge page. The top file on [this list](http://minecraft.curseforge.com/projects/techreborn/files?sort=releasetype) should always be the latest recommended release of Tech Reborn.

# Issues and Suggestions

To report an issue or make a suggestion, please head up to the `Issues` tab up above, and open a new issue. You will need a GitHub account for this (it's free!). **It is very important that you include the version of Tech Reborn you are using in your issue report.**

# Translation

Techreborn is available in a range of different languages, if you want to help out translate the mod please see our crowdin project at [https://translate.techreborn.ovh/](https://translate.techreborn.ovh/) The translations are automatically included in the jar files at build time.


# Credits

* Modmuss50 - Lead Developer
* Gigabit101 - Developer
* Prospector - Developer & Texture Artist
* Drcrazy - Developer
* Yulife - Lead Texture Artist
* The Chisel Team - Connected Textures if Chisel is installed

[![reborncore](https://i.imgur.com/NcOEWOh.png)](https://minecraft.curseforge.com/projects/reborncore/)


# License

Tech Reborn is licensed under the MIT license. Full license is  in **LICENSE.md**.

EMI 兼容使用的代码来自 [ExMI](https://github.com/Kneelawk/extra-mod-integrations) under CC0 License.

材质依旧来源于 GT Modern.