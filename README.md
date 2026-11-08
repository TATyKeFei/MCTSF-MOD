# Minecraft TSF MOD

> 这个项目还在早期开发测试的胚胎阶段，素材都是随便画的几笔
>
> 因此请先不要用于生产服务器，随时会更改导致存档炸档等操作，更新请随时备份存档！

这是一个 TSF 主题的玩法MOD

包含如: 人格排泄、附身、灵魂出窍、皮物玩法等

想知道本MOD的物品有啥用或咋玩，可以看本仓库的Wiki

一些人可能不喜欢这类没必要说出来，不干扰现实爱怎么想就怎么想，做梦梦到的东西更离谱

言论自由应该建立在尊重、不伤害他人的前提下

# 模组依赖关系

| MOD              | 用途                                 | 依赖性 | 版本    |
| :---             | :---                                 | :---   | :---    |
| Minecraft Java   | 游戏本体                             | 必须   | 26.3    |
| Java             | 游戏编程语言运行环境                 | 必须   | JDK 25+ |
| Fabric Loader    | 模组加载器                           | 必须   | 0.20.1  |
| Fabric api       | 加载、开发模组                       | 必须   | 0.20.1  |
| Trinkets Updated | 添加饰品栏装备饰品，皮物和拉链等需要 | 可选 (推荐)  | 4.2.1+26.3 +         |
| NeedsOfNature    | 用于添加附身、夺舍、穿皮等动画       | 可选 (推荐)  | 1.5.3-beta.2+26.3 +  |
| LuckPerms        | 服务器权限管理                       | 可选 (推荐)  | 支持当前服务端版本就行 |

## 不考虑支持的模组

我们不会联动适配这些模组

装载这些模组导致的问题我们也不会处理，除非非常严重的崩溃漏洞问题

| MOD                   | 原因     |
| :---                  | :---     |
| YesSteveModel/OpenYSM | 由于YSM模组自身争议版权原因，加上一些模型作者可能会对自己的模型被拿来做这种事反感导致冲突，因此不会兼容（同时也建议大家避免冲突请用自己画的/公共皮肤玩） |

# 下载与安装

你可在 [Releases](https://github.com/TATyKeFei/MCTSF-MOD/releases) 找到最新发布的稳定版本；如想体验开发中的功能，可在 [Action](https://github.com/TATyKeFei/MCTSF-MOD/actions) 中找到 Github 自动编译的最新版构建，不带-source名称的.jar模组就是

将模组jar本体拖进 客户端/服务端 根目录`./mods/`文件夹；需要客户端和服务端都安装模组才生效

# 构建

需要 Java 25、Fabric Loader 0.20.1 或更新版本，以及适用于 26.3 的 Fabric API。开发使用 Fabric API `0.165.2+26.3`

```sh
# 编译
./gradlew build


# 开发启动
./gradlew runClient` 或 `./gradlew runServer
```

# 替换纹理与图标

内置资源位于 `src/main/resources/assets/tsf/`，替换下表对应的 PNG 文件后运行 `./gradlew build`，新素材会随模组一起打包。每项使用独立文件，当前新增文件仍用手机图片占位。

| 用途 | 相对于 `src/main/resources/assets/tsf/` 的路径 |
| :--- | :--- |
| 模组图标（Mod Menu 等模组列表） | `icon.png` |
| TSF 创造模式分类页图标 | `textures/item/creative_tab_icon.png` |
| 催眠反制图腾 | `textures/item/hypnosis_protection_totem.png` |
| 附身反制图腾 | `textures/item/possession_protection_totem.png` |
| 皮物反制图腾 | `textures/item/skin_protection_totem.png` |
| TSF 反制图腾 | `textures/item/tsf_protection_totem.png` |
| 肉体再生图腾 | `textures/item/body_regeneration_totem.png` |
| 手机 | `textures/item/phone.png` |

皮刀、拉链、皮物、空肉体和两种长矛的独立贴图也在 `textures/item/`，人格史莱姆在 `textures/entity/slime.png`。玩家皮物仍会使用对应玩家的皮肤。

物品贴图推荐使用透明背景的正方形 PNG（16×16、32×32 等）；模组图标推荐 128×128 或 256×256。保留文件名即可，无须改代码。游戏内物品贴图还可通过普通资源包覆盖同名 `assets/tsf/` 路径并用 F3+T 重载；模组列表图标需要重新打包模组并重启游戏。

# 服务端设置

首次启动世界或服务端时生成 `config/tsf-server.json`; 修改后`/tsf config reload` 重载

多人游戏使用服务端文件，单人游戏使用当前实例的配置

可选安装 **Mod Menu 21.0.0**，从“模组 → TSF → 设置”打开界面。进入世界后读取当前服务器的实际设置，管理员保存后立即生效；普通玩家只能查看。在主菜单修改时保存到本机配置，供下次启动单人世界或本机服务端使用。Mod Menu 无须安装在服务端。

```json
{
  "skinKnifeEnabled": true,
  "skinKnifeRequiresHypnosis": false,
  "skinKnifeRequiresDowned": false,
  "expulsionDelaySeconds": 3,
  "deathLeavesBody": true
}
```

- `skinKnifeEnabled`：是否允许皮刀将玩家或空肉体皮化。默认开启，关闭后仍允许皮刀复原已放置的皮物
- `skinKnifeRequiresHypnosis`：开启后，目标必须被催眠才能皮化；空肉体须在催眠状态下被排出，避免通过先离体绕过规则
- `skinKnifeRequiresDowned`：开启后，必须安装兼容的自然之需，目标必须正在播放其标记为 `defeated` 的倒地动画。皮化前会结束倒地动画，再安全转移肉体与物品；普通睡眠、潜行或 TSF 附身动画不满足条件。空肉体也须保留倒地状态下离体的记录。未安装自然之需时禁止皮化并提示缺少依赖
- 两个前置条件默认关闭，可以分别开启；同时开启时须同时满足。`expulsionDelaySeconds` 控制人格排泄药水饮用后的延迟，范围 1～60 秒
- `deathLeavesBody`：默认 `true`，死亡地点留空肉体并以史莱姆复活；设为 `false` 时不因该次死亡生成空肉体，正常以肉体形态复活。已有的史莱姆死亡后也恢复肉体；其他已经存在的空肉体保留。两种方式均遵循原版物品掉落及 `keepInventory` 规则，修改对之后发生的死亡生效

# 许可证

本项目使用 [![GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

你可以自由地使用、修改和分发本项目的代码与素材

我们附加了一项要求：**任何分发或修改版本都必须保留本项目的版权声明和作者署名**
