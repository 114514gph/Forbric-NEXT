# 真·Mixin 织入 / ClassLoader 隔离 / 跨系桥接 — 实机验证报告

日期：2026-10-28　环境：JDK 21.0.2，MC 1.21.1 dedicated server，Fabric Loader 0.16.10

## 结论一句话
用真实 MC 1.21.1 server + 真实 Fabric Loader 0.16.10 + 真实 Sponge Mixin 0.8.7
启动了服务器，我们的测试 mixin **真的织进了 `net.minecraft.server.MinecraftServer`**，
钩子被回调打出唯一标记，服务器一路跑到 `Done (12.234s)`，全程 0 ERROR / 0 Exception。

证据日志见同目录 `real-fabric-launch.log`。

## 1. 环境与依赖（全部真实下载，非空壳）
工作目录 `/home/user/forbric-real/`，依赖走阿里云 Maven 镜像优先：

| 依赖 | 坐标 | 来源 | 说明 |
|---|---|---|---|
| 游戏本体 | server-1.21.1.jar (17.6MB) | Mojang bundler 解包 | `net.minecraft.server.MinecraftServer` 真实存在 |
| Fabric Loader | `net.fabricmc:fabric-loader:0.16.10` | fabricmc maven | 主类 `KnotServer` |
| Mixin | `net.fabricmc:sponge-mixin:0.15.4+mixin.0.8.7` | 阿里云 | Fabric 维护 fork，支持 JAVA_21 |
| MixinExtras | `io.github.llamalad7:mixinextras-common:0.4.1` | 阿里云 | |
| ASM | org.ow2.asm:*:9.7.1 | 阿里云 | |
| 官方 mixin | `org.spongepowered:mixin:0.8.5` | Sponge 官方仓库 | **不在 Maven Central**，且最高只支持 JAVA_18，织不动 Java 21 字节码 |

> 踩到的事实坑：任务要求的 `org.spongepowered:mixin:0.8.5` 实际不在 Maven Central
> （Central 上只有 configurate/spongeapi），要去 repo.spongepowered.org 下；且其
> `CompatibilityLevel` 枚举只到 JAVA_18，无法处理 1.21.1 的 class file 65。
> 因此**运行时**用同包名（`org.spongepowered.asm.*`）的 Fabric fork 0.8.7，编译 API 完全一致。

## 2. 新增模块 `forbric-next-mixin`
- `ForbricMixinService` — 封装 `MixinBootstrap.init()` / `MixinEnvironment` / `IMixinTransformer`，
  `init()` 设 side=SERVER、compat=JAVA_21；`registerConfig()` 注册 `.mixins.json`；
  `weave(name, bytes)` 暴露 transformer 给 classloader 钩子。
- `ForbricModClassLoader extends URLClassLoader` — parent-first 隔离：
  parent 持有 vanilla server.jar，mod jar 由本 CL 定义；defineClass 前过 mixin 织入。
- `MixinConfigReader` / `MixinJson` — 零依赖解析 `.mixins.json`。
- 测试 mixin `testmod.ForbricTestMixin`：`@Mixin(targets="net.minecraft.server.MinecraftServer")`
  `@Inject(method="<init>", at=@At("TAIL"))` 回调 `hook.ForbricHooks`。

## 3. 实机启动关键时间线（摘自 real-fabric-launch.log）
```
07:20:15  Loading Minecraft 1.21.1 with Fabric Loader 0.16.10
07:20:15  Loading 5 mods:
07:20:15    - forbric-test 1.0.0            ← mod 被发现
07:20:15  SpongePowered MIXIN Subsystem Version=0.8.7 Service=Knot/Fabric Env=SERVER
07:20:15  Compatibility level set to JAVA_21
07:20:22  Initializing MixinExtras ... (version=0.4.1)
07:20:23  No existing world data, creating new world
07:20:25  [STDOUT] [ForbricTestMixin] >>> FORBRIC-MIXIN-APPLIED net.minecraft.server.MinecraftServer 构造器已被织入并回调
07:20:38  Done (12.234s)! For help, type "help"
```
mixin 真织入的直接证据在第一次失败堆栈里更明显：
`MinecraftServer.handler$zza000$forbric-test$forbric$afterServerConstruct(MinecraftServer.java:2114)`
→ `MinecraftServer.<init>(MinecraftServer.java:322)`，注入的 handler 就在 vanilla 构造器里。

## 4. 实机踩到并修掉的坑
1. `ForbricHooks` 一开始放在 mixin 包 `...testmod` 内 → Sponge Mixin 报
   `IllegalClassLoadError: ... is in a defined mixin package ... cannot be referenced directly`。
   织入本身已成功，但 hooks 必须挪到 mixin 包外。已移到 `net.forbric.next.mixin.hook`。
2. classpath 同时挂官方 0.8.5 和 fork 0.8.7 → 同包名冲突，运行时只保留 fork。

## 5. ClassLoader 隔离 + 跨系桥接（独立 harness VerifyRealEnv）
对着真实 server-1.21.1.jar 跑：
```
ForbricModClassLoader: 定义 mod 类 modonly.SecretModClass (cl=ForbricModClassLoader@...)
  mod 类调用返回 = secret-from-mod-jar
  parent(system) classpath 上有 vanilla: .../server-1.21.1.jar!/net/minecraft/server/MinecraftServer.class
CrossTypeBridge: 快照 ItemStack id=minecraft:diamond count=32
  Forge ItemStack(count=32) -> FakeFabricStack count=32
```
- mod 专属类由 `ForbricModClassLoader` 定义；vanilla 类经 parent-first 由 parent 解析。
- `CrossTypeBridge` 反射读 Forge ItemStack 的 (id,count)，在 Fabric 侧重建，count 保真。

## 6. 构建
`./gradlew build` 全绿（45 tasks）。单测覆盖：mixins.json 解析、classloader 隔离、
CrossTypeBridge 转换、ForbricMixinService 真实接线（普通 JUnit 无 mixin host 时
正确抛出库里的 ServiceNotAvailableError，证明调到了真实库）。
