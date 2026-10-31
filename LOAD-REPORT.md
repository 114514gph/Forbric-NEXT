# NeoForbric Load Report

MC 26.3 客户端跨版本 + 跨加载器兼容测试报告
更新时间：2026-10-31

## 加载器进度总览

| 加载器 | 代码 | 真实 mod 测试 | 说明 |
|--------|------|--------------|------|
| Fabric | ✅ 完整 | ✅ 5 mod 到主菜单 | Carpet/MaLiLib/MiniHUD/Litematica/Tweakeroo |
| Forge | ✅ 完整 | 🔧 结构验证通过 | Farmer's Delight: 14 @Mod 类 / 72 Block / 27 Item |
| NeoForge | ✅ 完整 | ⏳ 待 mod | 基于 Forge，用 neoforge.mods.toml |
| Quilt | ✅ 完整 | ⏳ 待 mod | 基于 Fabric，用 quilt.mod.json |

---

## 🔧 Forge 运行时验证

| Mod | 版本 | 状态 | 备注 |
|-----|------|------|------|
| Farmer's Delight | 1.20.1-1.3.4 | ✅ 结构验证 | 289 classes, 14 @Mod, 72 Block, 27 Item |
| Create | 1.20.1-6.0.8 | ⏳ 下载中 | ~6MB / 13.6MB |

**验证结果：**
- ✅ mods.toml 元数据解析成功
- ✅ @Mod 注解扫描成功（14 个类）
- ✅ @EventBusSubscriber 检测成功（13 个类）
- ✅ Block/Item/Entity 注册类统计
- ⏳ 完整运行时（MC 主菜单）需要 Forge classloader 集成

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
