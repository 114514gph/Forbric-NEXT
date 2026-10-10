# Forbric-NEXT

下一代多生态系 Minecraft mod 加载器，在同一实例里同时运行 **Fabric**、**MinecraftForge**
和 **NeoForge** mod——而不重写各加载器内部的排序逻辑。

> **状态：早期骨架。** 这是 Forbric 0.3.0 的干净重设计，不是 fork。
> 项目可编译，能跨三个加载器跑通 9 阶段生命周期，并附带最小 fabric-api 事件桥。
> 移植发现、元数据解析和 mixin 接线是下一步里程碑。

## 为什么叫 "NEXT"？

Forbric 0.3.0 证明了多加载器概念可行，但积累了四个结构性问题：

1. **"强兼"越界**——boot 层自建统一依赖图，把 Fabric mod 和 Forge mod 混排，
   无视 Forge 原生 `ModSorter` 语义。
2. **per-mod canary 补丁**——35 个 canary 里有 8 个是为特定已发布 mod 打的硬编码补丁
   （Carpet、Create、C2ME……）。
3. **构建流水线耦合**——"主权内核"仍在消费旧 forbric-loader 脚本产出的 jar。
4. **静默桩**——生命周期回调空实现，`getFile()` 返回 null，`isDevelopmentEnvironment()`
   硬编码 false。

Forbric-NEXT 从干净的模块图出发，每个生态系拥有自己的发现、依赖解析和生命周期。
boot 层只做"跨系编排"。

## 模块结构

```
forbric-next-loader-api     SPI：ModLoader / LoadPhase / EventBus / BootOrchestrator
forbric-next-common         共享工具
forbric-next-fabric-api     最小 fabric-api 事件桥 + 语言适配器 SPI
forbric-next-fabric         Fabric 生态加载器（原生 mod-id 序）
forbric-next-forge          MinecraftForge 生态加载器（仅 BEFORE/AFTER 边）
forbric-next-neoforge       NeoForge 生态加载器
forbric-next-boot           跨系编排器
forbric-next-launcher       入口
```

### 核心契约

boot 编排器对每个加载器只调三个方法：`discover()` → `resolve()` → `runPhase(LoadPhase)`。
它永不重排某系内部 mod 列表，永不把跨系依赖塞进一张拓扑图，永不注入 per-mod 特判。

- **Fabric mod** 按 mod-id 字典序跑（对齐 Fabric Loader 原生）。
- **Forge/NeoForge mod** 按各自 `ModSorter` 产出的顺序跑（仅显式 BEFORE/AFTER 边；
  mandatory `depends` 是存在性要求，不是排序边）。
- **跨系依赖** 只做存在性检查，不进任何一组的内部拓扑图。

## 构建

需要 JDK 21+。

```bash
./gradlew build
./gradlew :forbric-next-launcher:run
```

Maven 默认走阿里云镜像，Gradle wrapper 走腾讯云镜像。

## 许可证

Apache-2.0，详见 [LICENSE](LICENSE)。
