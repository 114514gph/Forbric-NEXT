# Forbric-NEXT

**简体中文** | [English](README.md)

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Java](https://img.shields.io/badge/java-17%2B-orange)
![MC](https://img.shields.io/badge/mc-1.18%20%7C%201.20%20%7C%201.21%20%7C%2026.x-green)
![Status](https://img.shields.io/badge/status-alpha-yellow)

一个 Minecraft 实例，同时跑 Fabric、Forge、NeoForge 和 Quilt 的 mod。

---

## 为什么有这个项目

我折腾了一阵 [Forbric 0.3.0](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader)，想让它同时加载三个生态系的 mod。方向是对的，但到 0.3.0 发版的时候，架构已经积累了一堆我不重写就修不动的问题：

- **Merged-base 架构债务。** Forge 和 NeoForge 都改同一个 Minecraft 类，所以 Forbric 把它们合并成了一个字节码三合一 jar。这导致方法体位置变了，fabric-api 的 mixin 因为找不到目标方法而静默织入失败。修复方式是一个越列越长的 mixin 抑制清单（`MergedBaseMixinCompat`）——每一条都是为了让启动不崩而牺牲的功能。代码注释自己写着"这个清单是债，不是设计"。

- **强行统一 mod 加载顺序。** 不让 Fabric Loader 自己排 Fabric mod、Forge 自己的 `ModSorter` 排 Forge mod，而是把所有 mod 丢进一张 Kahn 拓扑图里重排。这意味着一个 Fabric mod 的依赖可以推拉一个 Forge mod 的加载名次——反过来也一样。而且这个排序比 Forge 原生的更严格（把 mandatory `depends` 当排序边，Forge 自己都不这么干）。

- **Per-mod canary 补丁。** 35 个 canary 模块里有 8 个就是硬编码的 `if (modId.equals("carpet"))` 特判——不是通用兼容层，是为了让某个测试 mod 能跑而打的补丁。

- **静默吞异常。** 几百个 catch 块要么没日志，要么只打 debug。mod 的 entrypoint 崩了、mixin config 拒绝加载、事件 listener 抛异常——没人知道，游戏假装没事继续跑。

- **旧 loader 还在给新内核兜底。** 号称"主权内核"，结果构建产物还得从旧 forbric-loader 的 shell 脚本里拿，还有一个 3800 行的字节码转换器专门在运行期把旧类名重定向到新类。

所以我不修了，重写。Forbric-NEXT 是一个干净的模块图，每个生态系自己管自己的发现、依赖解析和生命周期。boot 层只做"跨系编排"——绝不替别的加载器重排 mod 列表。

## 和 Connector / Kilt 有什么区别

你大概听说过 [Sinytra Connector](https://github.com/Sinytra/Connector) 或者 Kilt（Fabric-on-NeoForge 层）。它们是装在现有加载器上的翻译 mod——在 Forge 里重新实现 Fabric API，或者在 Fabric 里模拟 Forge 事件总线。能用，但总有一个生态系是"亲生"的，另一个是跑在垫片上的。

Forbric-NEXT 是加载器本身。没有宿主加载器被翻译。每个生态系的 mod 发现、元数据解析、entrypoint 排序、mixin 接线都走自己的模块。boot 编排器对每个加载器只调 `discover() → resolve() → runPhase()`，除此之外什么都不做。它不知道你的 mod id，不把跨系依赖塞进一张图。

**不过说清楚：** Connector 已经很成熟了，大多数人用它就行。Forbric-NEXT 还是 alpha 骨架。如果 Connector 已经能跑你的整合包，就用 Connector。Forbric-NEXT 是给想搞懂三系加载器底层怎么跑的人，或者想做一个不强迫某一系当宿主的加载器的人准备的。

## 安装方法

### 客户端（GUI 安装器）

需要 **JRE 17 或以上**。

1. 从[最新 Release](https://github.com/114514gph/Forbric-NEXT/releases/latest) 下载 `forbric-next-installer.jar`。
2. 运行：
   ```bash
   java -jar forbric-next-installer.jar
   ```
3. 下拉框选 Minecraft 版本（1.18.2 到 26.3）。
4. 确认 `.minecraft` 路径——会自动检测默认位置，你的启动器如果放在别处就点 **Browse…**。
5. 点 **Install**。安装器会在 `versions/` 下创建 `{mc版本}-forbric-next` 目录。
6. 打开启动器，新版本会出现在版本列表里。像启动其他版本一样启动就行。

卸载：点安装器的 **Uninstall** 按钮，或者直接删 `versions/{mc版本}-forbric-next` 目录。

### 专用服务端（命令行安装器）

需要 **JRE 17 或以上**。

```bash
# 纯 Forbric 服务端（默认）
java -jar forbric-next-server-installer.jar --mc 1.21.1 --dir /opt/mc-server --accept-eula

# 混合端（Forbric + Bukkit 插件，Mohist/Arclight 风格）
java -jar forbric-next-server-installer.jar --mc 1.21.1 --dir /opt/mc-server --server-mode mixed --accept-eula
```

| 参数 | 作用 |
|------|------|
| `--mc <版本>` | 为哪个 MC 版本安装 |
| `--dir <路径>` | 目标服务端目录 |
| `--server-mode pure\|mixed` | 纯 mod 服务端，还是带 Bukkit 插件支持的混合端（默认 pure） |
| `--accept-eula` | 自动写 `eula=true`（你仍然同意 Mojang 的 EULA） |
| `--help` | 显示所有参数 |

装完后编辑 `start.sh` / `start.bat` 调整 JVM 参数，然后运行。会自动写一个 `.forbric-next.toml` 配置文件。

## mod 放哪里

**所有 mod 放进同一个 `mods` 文件夹。** Forbric-NEXT 不按生态系分类——Fabric、Forge、NeoForge、Quilt 的 mod 全丢进去就行。

具体路径取决于启动器：
- 版本隔离的启动器（PCL2、HMCL）：`.minecraft/versions/{mc版本}-forbric-next/mods/`
- 非隔离的：你的 `.minecraft/mods/`（和其他版本共用的那个）

**Quilt mod 自动识别**：jar 根目录有 `quilt.mod.json` 就是 Quilt mod，只有 `fabric.mod.json` 就是 Fabric mod。不需要你手动区分。

**每个 mod 只放一个版本。** 如果一个 mod 同时有 Fabric 版和 Forge 版，只放一个进去。Forbric 不会报错，但也不会两个都跑。

## 当前状态

这是 **alpha**。编译能过，9 阶段生命周期能跑通，事件桥能正确触发——但还不能真正启动 Minecraft。

| 能跑的 | 还是骨架的 |
|--------|-----------|
| 四系（Fabric/Forge/NeoForge/Quilt）mod 识别 | 真正的 mod 发现（从 0.3.0 移植） |
| 最小 fabric-api 事件桥（服务端/客户端生命周期事件） | Mixin 接线 |
| 版本适配层 SPI（1.18/1.20/1.21/26.x 接口定义好了） | 功能性版本适配器（只有 26.x 标记了 functional） |
| Bukkit 混合端 SPI（接口定义好了） | Bukkit 插件加载器实现 |
| 客户端 GUI 安装器 + 服务端 CLI 安装器 | 真正的游戏文件下载（骨架只建目录结构） |
| 硬依赖缺失校验（启动前报错退出） | |

**支持的 MC 版本**（安装器版本表，已对照官方页面验证）：

| 版本 | Forge | NeoForge | Fabric API |
|------|-------|----------|------------|
| 1.18.2 | 40.3.12 | — | 0.14.24 |
| 1.19.2 | 43.5.2 | — | 0.14.25 |
| 1.20.4 | 49.2.9 | 20.4.0-beta | 0.15.10 |
| 1.21.1 | 52.1.16 | 21.1.45 | 0.16.14+1.21.1 |
| 1.21.11 | 61.2.1 | 21.11.40-beta | 0.16.14+1.21.11 |
| 26.3 | — | 26.3.0.76 | 0.155.2+26.3 |

Fabric Loader 基线版本 26.1.2（独立于 MC 版本号）。

## 出了问题怎么办

| 你看到的 | 怎么办 |
|----------|--------|
| 安装器提示 "requires Java 17+" | 装个新一点的 JRE。如果你已经能跑 Minecraft，大概率已经有了——把 `JAVA_HOME` 指过去。 |
| 服务端启动停在 "Unmet mandatory mod dependencies" | 某个 mod 缺前置。错误信息会列出具体缺什么。装上，或者在启动命令加 `-Dforbric.compatibilityPolicy=continue` 跳过。 |
| mod 装了但没反应 | 看 mods 文件夹旁边的 `.forbric-next/load-report.txt`。初始化失败的 mod 会列在那里。 |
| 混合端报 Bukkit 相关错误 | Bukkit SPI 目前只有接口定义，插件加载还没实现——mixed 模式现在只是写个 JVM 参数。 |
| Quilt mod 没被识别 | 确认 jar 根目录有 `quilt.mod.json`。有些"Quilt" mod 其实是 Fabric mod 带 Quilt 兼容——它们会被当 Fabric mod 加载，这是正常的。 |

## 从源码构建

需要 **JDK 21**。

```bash
git clone https://github.com/114514gph/Forbric-NEXT.git
cd Forbric-NEXT
./gradlew build
```

Maven 走阿里云镜像，Gradle 走腾讯云——在国内不需要自己配代理。

### 加新 MC 版本

1. 在 `forbric-next-version/.../impl/` 新建一个类实现 `VersionAdapter`。
2. 列出这个版本的类名、事件常量、mapping scheme。
3. **不要硬编码猜类名。** 用 [Linkie](https://linkie.shedaniel.dev) 查跨版本映射。1.19.4 以上 Mojang 官方发布了 mappings（游戏 jar 不再完全混淆）——用 Vineflower 反编译确认准确名字。
4. 在 `VersionAdapterRegistry.initializeBuiltins()` 注册你的适配器。

### 扩展 SPI

- 新加载器生态系：在 `forbric-next-loader-api` 实现 `ModLoader`，在 `forbric-next-boot` 注册。
- 新事件：在 `forbric-next-fabric-api` 对应的桥接口加方法。
- 新服务端模式：扩展 `forbric-next-loader-api/.../bukkit/` 里的 `MixedServerMode`。

## 致谢

基于 [Ray-T-r 的 Forbric 0.3.0](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader) 的教训重新设计。目标一样——一个实例跑三系 mod——架构不同。这个仓库 fork 自那个代码库，但从模块图开始重写了，旧 loader 的类结构没有搬过来。

感谢 Fabric、Forge、NeoForge、Quilt 团队做的 mod，让这件事值得折腾。

## 许可证

Apache-2.0，详见 [LICENSE](LICENSE)。
