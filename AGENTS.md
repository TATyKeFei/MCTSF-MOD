# 仓库贡献指南

## 项目结构与模块划分

TSF 是面向 Minecraft 26.3 的 Fabric 模组，使用 Java 25。依赖和模组版本统一配置在 `gradle.properties`。

- `src/main/java/icu/tatyrealms/tatykefei/`：公共及服务端代码，按物品（`item/`）、状态（`state/`）、网络（`network/`）、兼容（`compat/`）和注入（`mixin/`）划分。
- `src/client/java/icu/tatyrealms/tatykefei/client/`：界面、渲染、旁观同步及客户端 Mixin。
- `src/main/resources/assets/tsf/`：内置纹理、物品模型、翻译和模组图标。
- `src/main/resources/data/`：配方、动画定义及 Trinkets 饰品槽定义和标签。
- `.github/workflows/build.yml`：自动构建并上传 JAR 产物。`build/` 和 `run/` 为生成目录。

## 构建、测试与开发命令

使用 JDK 25 和仓库自带的 Gradle Wrapper：

- `./gradlew build`：编译公共及客户端代码，执行已配置的检查，将 JAR 输出至 `build/libs/`。
- `./gradlew test`：运行测试；目前没有测试源码，结果为 `NO-SOURCE`。
- `./gradlew runClient`：启动开发客户端。
- `./gradlew runServer`：启动开发服务端；使用前需接受其 EULA。
- `./gradlew --offline build`：首次配置完成后，使用已缓存的依赖离线构建。

发布时选择普通 JAR；`-sources.jar` 是源码包。

## 代码风格与命名约定

Java 使用四空格缩进、同行左花括号及 UTF-8 编码。包名统一以 `icu.tatyrealms.tatykefei` 开头。类型名使用 PascalCase，成员使用 camelCase，常量使用 UPPER_SNAKE_CASE，Mixin 新增成员使用 `tsf$` 前缀。资源标识使用 `tsf` 命名空间和 snake_case 路径。新增界面文本时同步更新 `zh_cn.json` 与 `en_us.json`。JSON 格式遵循相邻文件；目前未配置格式化或静态检查工具。

## 测试要求

目前没有自动化测试套件、已配置的测试框架或覆盖率要求。修改后运行 `./gradlew build`，并手动验证受影响的行为。附身、旁观物品栏、聊天和断线相关修改需使用两个客户端验证；渲染修改需检查相关展示场景。兼容修改需分别验证安装和未安装 Trinkets Updated、NeedsOfNature 的情况。提交说明中记录已执行的检查及尚未验证的行为。

## 兼容与状态管理

公共代码不得引用客户端专用类。可选兼容功能必须能够在对应模组缺失时正常运行。Minecraft 状态修改应在所属游戏线程执行；后台持久化仅接收与实时状态分离的快照。服务端配置位于 `config/tsf-server.json`。

## 提交与合并请求规范

提交记录通常使用简洁的中文标题描述具体改动，例如 `修复展示框中肉体和皮物被压扁及悬空的问题`。每完成一项任务单独提交，保留无关修改，未经明确要求不得推送。除非用户要求，否则保持模组版本号不变。合并请求应说明修改后的行为、关联相关议题、列出验证结果；涉及视觉变化时附上截图。
