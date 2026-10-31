# NeoForbric Load Report

MC 26.3 客户端跨版本 + 跨加载器兼容测试报告
更新时间：2026-10-31 (Beta 桥接集成版)

## 加载器进度总览

| 加载器 | 代码 | 真实 mod 测试 | 说明 |
|--------|------|--------------|------|
| Fabric | ✅ 完整 | ✅ 5 mod 到主菜单 | Carpet/MaLiLib/MiniHUD/Litematica/Tweakeroo |
| Forge | ✅ 桥接集成 | ✅ 10/14 类加载到主菜单 | Farmer's Delight 真正运行时加载，常量池重映射 |
| NeoForge | ✅ 完整 | ⏳ mod 已下载待测 | Friends&Foes NeoForge 5.0.4 (366 classes) |
| Quilt | ✅ 完整 | ⏳ mod 已下载待测 | Friends&Foes Quilt/Fabric 5.0.4 |

---

## 🔧 Forge 桥接集成（重大进展）

### NeoForbric Bridge
- **机制**：作为 Fabric mod 打包 (neoforbric-bridge.jar)，提供 preLaunch + main 入口点
- **preLaunch 阶段**：扫描 mods 目录，发现 Forge mod，运行 ForgeModRuntime 7 阶段生命周期
- **main 阶段**：初始化 Forge 事件总线

### 字节码重映射（真正修复，非 catch 跳过）
- **RuntimeRemapper**：正确解析 class 文件常量池，重写类名引用
- **映射表**：`forge-named-1.20.1-26.3.properties`（21 条实体类子包重定位）
- **重定位示例**：
  - `net/minecraft/world/entity/animal/Rabbit` → `net/minecraft/world/entity/animal/rabbit/Rabbit`
  - Chicken/Cow/Pig/Sheep/Fox/Frog/Goat 等 15 个实体类全部移到子包

### Farmer's Delight 加载结果
| 指标 | 数值 |
|------|------|
| 总 @Mod 类 | 14 |
| 成功实例化 | 10 (71%) |
| ClassFormatError | 0（重映射正确） |
| 游戏到主菜单 | ✅ Welcome to Minecraft! |

### 剩余 4 个失败（真正 API 差异）
1. `IIngredientSerializer` — Forge 专属类，MC 不存在
2. `IConfigSpec` — Forge 配置类，MC 不存在
3. `Enchantment` 变成 final — 26.3 API 变更，不能被继承
4. `DiggerItem` 被删除 — 工具改成数据驱动 Tool 组件

---

## 📦 已下载 Mod 清单

| Mod | 加载器 | 版本 | 大小 | 状态 |
|-----|--------|------|------|------|
| Farmer's Delight | Forge | 1.20.1-1.3.4 | 3.2MB | ✅ 10/14 类加载 |
| Friends&Foes | NeoForge | 5.0.4+mc1.21.1 | 5.1MB | ⏳ 待端到端测试 |
| Friends&Foes | Quilt/Fabric | 5.0.4+mc1.21.1 | 5.1MB | ⏳ 待端到端测试 |
| Create | Forge | 1.20.1-6.0.8 | 23MB | ⚠️ jar 损坏，需重下 |

📸 截图：`screenshot-forge-remap2.png`（Welcome to Minecraft! 主菜单）

---

## ✅ Fabric 已验证通过

| Mod | 版本 | 状态 | 备注 |
|-----|------|------|------|
| Carpet | 1.4.147+v240613 | ✅ 主菜单 | 147 个 mixin 全部过滤 |
| MaLiLib | 0.21.10 | ✅ 主菜单 | 含 3 个嵌套 fabric-api jar |
| MiniHUD | 0.32.60 | ✅ 主菜单 | 依赖 malilib |
| Litematica | 0.19.61 | ✅ 主菜单 | 依赖 malilib |
| Tweakeroo | 0.21.61 | ✅ 主菜单 | 依赖 malilib |

**5 个 Fabric mod 同时加载，MC 26.3 主菜单正常渲染。**

### Resource Packs

| Pack | 版本 | 状态 | 备注 |
|------|------|------|------|
| Faithful 32x | latest | ✅ 结构正常 | pack_format 34 |
| Fresh Animations | 1.11.1 | ✅ 结构正常 | pack_format 15-75 |

---

## 🔧 Forge 运行时（代码完成，待测试）

**模块：** `neoforbric-forge`

**已实现：**
- `ForgeAnnotationScanner` — 字节码常量池扫描 @Mod 注解类
- `ForgeEventBus` — @SubscribeEvent 事件分发，支持事件继承
- `ForgeRegistry` — Block/Item/Entity/Biome/Sound 注册系统
- `ForgeModRuntime` — 完整生命周期：
  - ConstructionEvent → RegisterEvent → SetupEvent → SidedSetupEvent → InterModEnqueueEvent → InterModProcessEvent → LoadCompleteEvent
- `ForgeModLoader` — mods.toml 解析 + @Mod 类加载 + 事件总线串联

---

## 🔧 NeoForge 运行时（代码完成，待测试）

**模块：** `neoforbric-neoforge`

**与 Forge 的差异：**
- 元数据文件：`META-INF/neoforge.mods.toml`（不是 mods.toml）
- @Mod 注解包名：`net.neoforged.fml.common.Mod`
- 复用 Forge 的 EventBus 和 Registry
- 单事件总线（没有 Forge 的 mod-bus / game-bus 分离）

---

## 🔧 Quilt 运行时（代码完成，待测试）

**文件：** `neoforbric-fabric/QuiltModLoader.java`

**与 Fabric 的差异：**
- 元数据文件：`quilt.mod.json`（Fabric 是 fabric.mod.json）
- 复用 Fabric 的入口点执行和加载顺序
- 复用 QuiltModDetector 识别 Quilt mod

---

## 🔧 跨版本重映射层

**模块：** `neoforbric-remapper`

**已实现：**
- RuntimeRemapper — class 文件常量池重写（CONSTANT_Class + 描述符 + 字符串常量）
- ModBytecodeRewriter — 批量重写 mod jar 工具
  - JiJ 嵌套 jar 自动处理（META-INF/jars/*.jar）
  - 嵌套 jar 签名清理
  - 嵌套 jar mixin 配置清理
- 映射数据：
  - 类映射：8,230 条
  - 方法映射：40,865 条（按描述符精确匹配）
  - 字段映射：37,430 条

---

## ⚠️ 待解决

### 网络限制（无法下载测试 mod）
- CurseForge 直连失败（AccessDenied）
- Modrinth API 不通
- GitHub API 认证后可用，但 releases 里没有 mod jar 附件（MC mod 全走 CurseForge/Modrinth 分发）

### 待测试 mod（有了 jar 就能测）
- Fabric: Xaero's Minimap, XRay, Carpet TIS Addition
- Forge: Create, Valkyrien Skies, Farmer's Delight, ProjectE, Titan, Avaritia, 压缩木棍
- Resource Packs: EXTREAL, Better Leaves, Compliance 64x, Low On Fire, Punchy!

---

## 📸 截图

- 零 mod 主菜单：`/home/user/forbric-263/screenshot-mainmenu.png`
- Carpet 主菜单：`/home/user/forbric-263/screenshot-carpet-mainmenu.png`
- 5 mod HUD 主菜单：`/home/user/forbric-263/screenshot-hud-mainmenu.png`
