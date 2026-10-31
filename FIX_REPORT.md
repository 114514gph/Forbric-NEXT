# Forbric Bug 修复报告 & NeoForbric 架构改进

> 基于 Forbric 0.3.0 (commit 520d7aa, target MC 26.2) 的深度代码审查与修复。
> 审查范围：417 个 kernel Java 文件 + 140 个 runtime 文件 + 74 个旧 loader 文件 + 35 个 canary 模块。

---

## 一、用户已知 Bug 核实

### Bug 1: "Fabric 模组兼容层根本没用"
**核实结果：半误判。** 兼容层是真实工作的——`fabric.mod.json` 解析、Entrypoint 反射实例化（`Class.forName().newInstance()`）、Mixin 配置注册（`Mixins.addConfiguration`）、JiJ 嵌套解压、类加载隔离（ForbricClassLoader child-first）全部完整。`MOD_TEST_FAILURES.md` 记录 130 个纯 Fabric mod 中 119 个对照原生 Fabric 两边都过。

**真正的断点（已修/已记录）：**
- `isDevelopmentEnvironment()` 硬编码 `false` → 已修为 `-Dforbric.dev=true` 可切换
- 顶层 mod 缺失硬依赖不报错（只做拓扑排序）→ 已记录为已知限制
- 自定义 entrypoint key 类加载失败时静默跳过 → 已有 SUSPECTED finding 记录
- kernel 不自带 fabric-api，事件总线依赖用户安装的 fabric-api mod 的 mixin 织入 → 这是设计决策，不是 bug

### Bug 2: "一堆函数没有启用"
**核实结果：没有致命级空实现。** 扫描 631 个 Java 文件，关键路径（entrypoint 调用、事件分发、类加载委托）上没有"写了但没接"的桩。发现 12 处重要问题和 16 处次要问题，其中关键的几处已修：
- `ForbricNeoBridgeMixinPlugin` 四个生命周期方法全空 → 已加日志
- `ForbricMixinService.registerInvalidClass/addTransformerExclusion` 空体 → 已加并发 set 存储
- `KernelForgeModFileInfo.getFile()` 返回 null → 已改为抛 IllegalStateException

### Bug 3: "加载顺序按自己设定，不是各模组加载器自己的顺序（强兼）"
**核实结果：Fabric 侧已尊重原生，Forge/NeoForge 侧确实越界。**
- Fabric mod 已按 mod-id 字典序排列（对齐 Fabric Loader 原生），entrypoint 严格按 preLaunch→main→client/server 分组执行 ✅
- **问题**：`KernelModLoader.orderByDependency` 把 Fabric mod 也拉进了 Forge @Mod 的 Kahn 拓扑图
- **问题**：`ModConstructionOrder` 把每个 mandatory `depends` 都加成排序边（Forge 原生 `ModSorter` 只认显式 BEFORE/AFTER）
- **已修**：删除 Fabric mod 入图，去掉 mandatory-depends 排序边

### Bug 4: "canary 是为特定测试 mod 适配的"
**核实结果：35 个 canary 模块中 8 个是 per-mod 硬编码补丁。**
- **per-mod 补丁（应删除特判）**：carpet, create-fly, create-adapted, c2me-barrel-roll, corpse-render, hopper-storage, ipn-screen, nesteddupe
- **通用基础设施（应沉淀为 API）**：21 个（event-chain, transfer, damage-events, server-events, tooltips 等）
- **测试桩（保留）**：7 个（broken, fabric, mixin-outcome, attribution 等）

### Bug 5: "旧 forbric-loader 还在为新 kernel 兜底"
**核实结果：编译期 0 依赖，但构建流水线耦合。**
- Gradle 层 0 project dep，源码层 0 `import net.forbric.loader.*`
- **真正耦合**：kernel 的 runtime sourceSet 编译依赖旧 loader pipeline 产出的三个 jar（merged-base + forge/neo runtime），路径写死 `../forbric-loader/run/`
- **字节码缝**：`ForbricMergedBaseCompatTransformer` 硬编码 3 条旧类名→interop 类的 ASM 重写
- NeoForbric 已将此列为头号架构改进：把 MergedBaseBuilder 收编进 kernel 仓库

---

## 二、已落地的修复（6 处，编译验证通过）

| # | 修复 | 文件 | 改动 |
|---|------|------|------|
| 1 | Fabric mod 拆出 Forge 拓扑图 | `KernelModLoader.java` | 删除 `known.addAll(ModPresence.fabricMods())` |
| 2 | mandatory depends 不当排序边 | `ModConstructionOrder.java` | 去掉 `\|\| dep.isMandatory()`，只留显式 AFTER |
| 3 | dev 环境可配置 | `KernelFabricLoader.java` | `return false` → `return Boolean.getBoolean("forbric.dev")` |
| 4 | getFile() NPE 隐患 | `KernelForgeModFileInfo.java` | `return null` → 抛带 modId 的 IllegalStateException |
| 5 | NeoBridge mixin 插件可观测 | `ForbricNeoBridgeMixinPlugin.java` | onLoad/preApply/postApply 加 JUL 日志 |
| 6 | MixinService 诊断完善 | `ForbricMixinService.java` | invalidClasses/transformerExclusions 加并发 set + getter；空回调加 no-op 注释 |

编译验证：`JAVA_HOME=<jdk21> ./gradlew --no-daemon compileJava compileTestJava` → **BUILD SUCCESSFUL**

---

## 三、NeoForbric 架构改进

### 设计原则（从 0.3.0 的教训中提炼）

1. **按加载器隔离**：Fabric / Forge / NeoForge 是独立 Gradle 模块，各自拥有自己的发现、依赖图、entrypoint 排序和生命周期。boot 模块只做"跨系编排"，永不重排某系内部的 mod 列表。
2. **无 per-mod canary 补丁**：兼容 hook 作为通用服务接口（事件总线、注册窗口、类加载策略）存在，不再有 `if (modId.equals("carpet"))` 分支。
3. **自包含**：不依赖旧 forbric-loader。MergedBaseBuilder 将收编进 kernel 仓库。
4. **尊重原生顺序**：Fabric mod 组内按 Fabric Loader 的 id 序跑；Forge/NeoForge mod 组内按 Forge 自己的 ModSorter 跑（显式 BEFORE/AFTER 边，mandatory depends 只是 presence 要求）。NeoForbric 只决定"先开哪扇窗、窗里跑哪一系"。

### 模块结构

```
NeoForbric/
├── settings.gradle / build.gradle      # 根配置，阿里云 Maven 镜像
├── neoforbric-loader-api/            # SPI 定义（ModLoader, LoadPhase, EventBus, BootOrchestrator）
├── neoforbric-common/                # 共享工具（NextLog）
├── neoforbric-fabric/                # Fabric 生态加载器（移植 FabricModDiscovery + FabricLoadOrder）
├── neoforbric-forge/                 # MinecraftForge 生态加载器（移植 Forge ModSorter 语义）
├── neoforbric-neoforge/              # NeoForge 生态加载器
├── neoforbric-boot/                  # 跨系编排器（DefaultBootOrchestrator）
└── neoforbric-launcher/              # 入口 Main
```

### 关键接口契约

- **`ModLoader`**：每个生态加载器实现 `discover()` / `resolve()` / `runPhase(LoadPhase)`。boot 模块只调这三个方法，不窥探内部 mod 列表。
- **`LoadPhase`**：9 个阶段枚举（PRE_LAUNCH → CONSTRUCT → REGISTRY → COMMON_SETUP → SIDED_SETUP → IMC_ENQUEUE → IMC_PROCESS → LOAD_COMPLETE → GAME_STARTED）。
- **`EventBus<T>`**：统一事件总线抽象，三端各有 adapter，取代散落在 3800 行 Transformer 里的 hook 点。
- **`ClassLoadingPolicy`**：类加载委托策略作为一等接口，取代手写字符串前缀表。

### 编译验证

```bash
cd NeoForbric
JAVA_HOME=<jdk21> ./gradlew --no-daemon build   # BUILD SUCCESSFUL
./gradlew --no-daemon :neoforbric-launcher:run  # 跑通 9 阶段生命周期
```

---

## 四、仍待后续处理的已知限制

1. **顶层 mod 硬依赖缺失不报错**：Fabric 原生会硬失败，Forbric 当前只 WARN。NeoForbric 的 `resolve()` 应在后期加入严格依赖校验。
2. **MergedBaseBuilder 收编**：kernel 仍依赖旧 loader pipeline 产出的 merged-base jar。NeoForbric 后期需把这个构建脚本搬进自己的仓库。
3. **fabric-api 事件桥**：kernel 不自带 fabric-api，事件总线靠用户安装的 fabric-api mod。NeoForbric 应考虑提供一个最小事件桥（至少 ServerLifecycleEvents / ClientLifecycleEvents），让基础 Fabric mod 不装 fabric-api 也能跑。
4. **Kotlin/Scala 语言适配器**：不随包发布，Kotlin mod 需用户自行安装 fabric-language-kotlin。
5. **8 个 per-mod canary 补丁的清理**：在 NeoForbric 中，carpet/create-fly/c2me 等特判应被通用事件 API 吸收。当前骨架保留了方向，具体移植在后续迭代。
