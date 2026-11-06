# TSF

这是一个 TSF 主题的玩法MOD

包含如: 人格排泄、附身、灵魂出窍、皮物玩法等

想知道本MOD的物品有啥用或咋玩，可以看本仓库的Wiki

# 模组依赖关系

| MOD              | 用途                                 | 依赖性 | 版本    |
| :---             | :---                                 | :---   | :---    |
| Minecraft        | 游戏本体                             | 必须   | 26.3    |
| Fabric Loader    | 模组加载器                           | 必须   | 0.20.1  |
| Fabric api       | 加载、开发模组                       | 必须   | 0.20.1  |
| Trinkets Updated | 添加饰品栏装备饰品，皮物和拉链等需要 | 可选 (推荐)  | 4.2.1+26.3 +         |
| NeedsOfNature    | 用于添加附身、夺舍、穿皮等动画       | 可选 (推荐)  | 1.5.3-beta.2+26.3 +  |

# 构建

需要 Java 25、Fabric Loader 0.20.1 或更新版本，以及适用于 26.3 的 Fabric API。开发使用 Fabric API `0.165.2+26.3`

```sh
# 编译
./gradlew build


# 开发启动
./gradlew runClient` 或 `./gradlew runServer
```

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

根据 GPLv3 第 7(b) 条，我们附加了一项要求：**任何分发或修改版本都必须保留本项目的版权声明和作者署名**