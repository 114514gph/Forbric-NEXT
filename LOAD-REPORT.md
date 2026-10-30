# Forbric-NEXT 跨版本加载报告

## 里程碑

### ✅ 已达成
- [x] 零 mod 状态下 MC 26.3 客户端跑到主菜单
- [x] carpet mod (1.21.1) 成功加载并跑到主菜单
- [x] 字节码重写器工作正常（715 classes rewritten）
- [x] 完整映射表生成（8226 类 / 40865 方法 / 37430 字段）

## 映射表数据

| 类型 | 数量 | 说明 |
|------|------|------|
| 类映射 | 8,226 | Mojang ProGuard + Fabric intermediary 交叉引用 |
| 方法映射 | 40,865 | 按描述符精确匹配 |
| 字段映射 | 37,430 | 按 owner 作用域匹配 |

## 手动添加的重定向映射

| 旧路径 (1.21.1) | 新路径 (26.3) | 原因 |
|-----------------|---------------|------|
| `world/level/GameRules` | `world/level/gamerules/GameRules` | 包路径移动 |
| `world/level/GameRules$Key` | `world/level/gamerules/GameRules$Key` | 包路径移动（但 Key 内部类已被重构掉） |
| `world/level/levelgen/DensityFunction` | `world/level/levelgen/densityfunction/DensityFunction` | 包路径移动 |
| `world/level/levelgen/DensityFunction$FunctionContext` | `world/level/levelgen/densityfunction/DensityFunction$CompileContext` | 内部类改名 |
| `world/level/Explosion` | `world/level/ServerExplosion` | 类拆分 |
| `resources/ResourceLocation` | `resources/Identifier` | 类改名 |

## 已过滤的 Mixin

### carpet (全部 147 个 mixin 暂时清空)
原因：carpet 的 mixin 大量引用内部 API，这些 API 在 26.3 里很多都变了。
策略：先清空所有 mixin 证明 mod 能加载，后续逐个加回测试。

### Fabric API (已知有问题的模块)
| 模块 | 问题 |
|------|------|
| fabric-crash-report-info-v1 | SystemDetailsMixin setDetail 签名不匹配 |
| fabric-events-interaction-v0 | MinecraftClientMixin addCustomNbtData 方法没了 |
| fabric-networking-api-v1 | MinecraftClientAccessor integratedServerConnection 字段没了 |
| fabric-screen-api-v1 | ScreenMixin/ScreenAccessor Screen 类结构变了 |
| fabric-client-tags-api-v1 | 待测试 |
| fabric-rendering-v1 | 待测试 |
| fabric-resource-loader-v0 | 待测试 |

## 已修复的关键问题

1. **refmap 覆盖字节码重写** → 删掉所有 mod jar 里的 refmap
2. **JiJ 嵌套 jar 没处理**（Fabric API 49 个模块）→ 解压到 mods/ 单独重写
3. **SHA-256 签名摘要不匹配** → 去掉 META-INF 签名文件
4. **LONG/DOUBLE 常量池解析错误** → 占两个槽位，修复后重写数量增加 10-20%
5. **映射链单 pass 问题** → HashMap 无序导致应用顺序不确定，修复为多 pass

## 截图证据

- `screenshot-mainmenu.png` — 零 mod 主菜单
- `screenshot-carpet-mainmenu.png` — carpet mod 加载后主菜单

## 下一步

1. 逐个加回 carpet 的 mixin，修复有问题的
2. 逐个加回 fabric-api 模块，找出哪些有问题
3. 游戏内启用材质包验证贴图
4. Forge mod 兼容性测试
