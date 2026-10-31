# NeoForbric 跨版本兼容测试报告

## 测试环境
- MC 版本：26.3
- 测试环境：`/home/user/forbric-263/`
- 映射表：8,228 类 / 40,865 方法 / 37,430 字段

---

## Fabric Mod 测试结果

| Mod | 状态 | 重写类数 | 问题 |
|-----|------|----------|------|
| Carpet | ✅ PASS | 715 | 全部 mixin 移除后成功加载到主菜单 |
| MaLiLib | ⚠️ 部分通过 | 491 | Mixin 阶段通过，运行时报 class_2960 (ResourceLocation) 未重写 |
| MiniHUD | ⚠️ 部分通过 | 283 | 同上 |
| Litematica | ⚠️ 部分通过 | 413 | 同上 |
| Tweakeroo | ⚠️ 部分通过 | 184 | 同上 |
| Carpet TIS Addition | ❌ FAIL | - | 文件损坏（不是有效 zip） |
| Xaero's Minimap | ⏸️ 未下载 | - | 需要下载 |
| X光/XRay | ⏸️ 未下载 | - | 需要下载 |

---

## Forge/NeoForge Mod 测试结果

| Mod | 状态 | 问题 |
|-----|------|------|
| Create / 机械动力 | ⏸️ 未下载 | CurseForge 直连被拒 |
| Valkyrien Skies / 瓦尔基里 | ⏸️ 未下载 | 需要下载 |
| Farmer's Delight / 农夫乐事 | ⏸️ 未下载 | 需要下载 |
| ProjectE / 等价交换 | ⏸️ 未下载 | 需要下载 |
| Titan / 泰坦 | ⏸️ 未下载 | 需要下载 |
| Avaritia / 无尽贪婪 | ⏸️ 未下载 | 需要下载 |
| 压缩木棍 / Compressed Stick | ⏸️ 未下载 | 需要下载 |

---

## 材质包测试结果

| 材质包 | 状态 |
|--------|------|
| Fresh Animations | ✅ 已就位，待游戏内启用测试 |
| Faithful 32x | ✅ 已就位，待游戏内启用测试 |
| EXTREAL | ⏸️ 未下载 |
| Better Leaves | ⏸️ 未下载 |
| Compliance 64x | ⏸️ 未下载 |
| Low On Fire | ⏸️ 未下载 |
| Punchy! | ⏸️ 未下载 |

---

## 手动添加的重定向映射

| 旧路径 (1.21.1) | 新路径 (26.3) | 原因 |
|-----------------|---------------|------|
| `world/level/GameRules` | `world/level/gamerules/GameRules` | 包路径移动 |
| `world/level/levelgen/DensityFunction` | `world/level/levelgen/densityfunction/DensityFunction` | 包路径移动 |
| `world/level/levelgen/DensityFunction$FunctionContext` | `world/level/levelgen/densityfunction/DensityFunction$CompileContext` | 内部类改名 |
| `client/gui/screens/ReceivingLevelScreen` | `client/gui/screens/LevelLoadingScreen` | 类改名 |
| `client/gui/screens/ReceivingLevelScreen$Reason` | `client/gui/screens/LevelLoadingScreen$Reason` | 内部类跟着改 |
| `world/level/Explosion` | `world/level/ServerExplosion` | 类拆分 |
| `resources/ResourceLocation` | `resources/Identifier` | 类改名 |

---

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

---

## 截图证据

- `screenshot-mainmenu.png` — 零 mod 主菜单
- `screenshot-carpet-mainmenu.png` — carpet mod 加载后主菜单

---

## 下一步

1. 继续修 malilib 的 mixin（method_1574 → 找新方法名）
2. 修好 malilib 后一起测 litematica/minihud/tweakeroo
3. 下载缺失的 Fabric mod（Xaero's, XRay）
4. 尝试下载 Forge mod（CurseForge 直连被拒，需要找其他源）
5. 游戏内启用材质包验证贴图效果
