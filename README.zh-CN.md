# Forbric-NEXT

**简体中文** | [English](README.md)

在同一个实例里跑 Fabric、Forge、NeoForge 的 mod——而不是让加载器自己跟自己打架。

## 为什么有这个项目

我折腾了一阵 [Forbric 0.3.0](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader)，
想让它能同时加载三个生态系的 mod。方向是对的，但架构慢慢变成了我自己都维护不动的样子：

- boot 层自建了一张大依赖图，把 Forge mod 和 Fabric mod 混在一起排序，
  完全不管 Forge 原生 `ModSorter` 是怎么工作的。
- 35 个 canary 模块里有 8 个就是硬编码的 `if (modId.equals("carpet"))`，
  为了让某个特定 mod 能跑起来。
- 号称"主权内核"，结果构建产物还得靠旧 loader 的 shell 脚本吐出来。
- 一半生命周期回调是空桩，没人发现。

所以我不修了，重写。Forbric-NEXT 是一个干净的模块图，每个生态系自己管自己的发现、
依赖解析和生命周期。boot 层只做"跨系编排"——绝不替别的加载器重排 mod 列表。

## 快速开始

需要 JDK 21+。

```bash
git clone https://github.com/114514gph/Forbric-NEXT.git
cd Forbric-NEXT
./gradlew build
./gradlew :forbric-next-launcher:run
```

就这样——不需要 `bootstrap.sh`，不需要预先下载 jar，不需要设环境变量。
Maven 走阿里云，Gradle 走腾讯云。

## 项目结构

```
Forbric-NEXT/
├── forbric-next-loader-api/     # SPI：ModLoader / LoadPhase / EventBus / BootOrchestrator
├── forbric-next-common/         # 共享工具
├── forbric-next-fabric-api/     # 最小 fabric-api 事件桥 + 语言适配器 SPI
├── forbric-next-version/       # 版本适配层（1.18 / 1.20 / 1.21 / 26.x 骨架）
├── forbric-next-fabric/        # Fabric 生态加载器
├── forbric-next-forge/         # MinecraftForge 生态加载器
├── forbric-next-neoforge/      # NeoForge 生态加载器
├── forbric-next-boot/           # 跨系编排器
└── forbric-next-launcher/      # 入口
```

核心契约：boot 编排器对每个加载器只调 `discover() → resolve() → runPhase()`，
除此之外什么都不做。它不知道你的 mod id，不把跨系依赖塞进一张图，不写 per-mod 特判。

## 当前状态

早期骨架。编译能过，9 阶段生命周期能跑通，fabric-api 事件桥能正确触发。
还没有的东西：

- 真正的 mod 发现（从 0.3.0 移植）
- Mixin 接线
- 真正启动 Minecraft
- 1.18 / 1.20 / 1.21 的版本适配器只是接口骨架，只有 26.x 有功能性实现

想帮忙的话，下一步是把 0.3.0 的 `FabricModDiscovery` 和 `FabricLoadOrder`
移植到 `forbric-next-fabric`。

## 致谢

基于 [Ray-T-r 的 Forbric 0.3.0](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader)
的教训重新设计。目标一样，架构不同。这个仓库 fork 自那个代码库，但从模块图开始重写了——
旧 loader 的类没有搬过来。

## 许可证

Apache-2.0，详见 [LICENSE](LICENSE)。
