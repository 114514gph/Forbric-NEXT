# Forbric-NEXT Load Report

MC 26.3 客户端跨版本 + 跨加载器兼容测试报告
测试时间：2026-10-31

## 加载器进度总览

| 加载器 | 状态 | 说明 |
|--------|------|------|
| Fabric | ✅ 已验证 | 5 个 mod 跑到主菜单 |
| Forge | 🔧 开发中 | 运行时骨架已写，待真实 mod 验证 |
| NeoForge | ❌ 未开始 | 复用 Forge 代码，适配 @Mod 注解差异 |
| Quilt | ❌ 未开始 | 复用 Fabric 代码，适配 Quilt Loader API |

---

## ✅ Fabric 已验证通过

| Mod | 版本 | 状态 | 备注 |
|-----|------|------|------|
| Carpet | 1.4.147+v240613 | ✅ 主菜单 | 147 个 mixin 全部过滤（类结构变化） |
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

## 🔧 Forge 运行时（开发中）

`forbric-next-forge` 模块当前状态：
- ForgeModLoader.java（102 行）
- ForgeModRuntime.java（101 行）

待实现：
- @Mod 注解扫描 + mod 元数据解析（mods.toml）
- 生命周期调度（preInit / init / postInit）
- EventBus 桥接
- 真实 Forge mod 加载验证

### 待测试 Forge Mods
- Create 机械动力
- Valkyrien Skies 瓦尔基里
- Farmer's Delight 农夫乐事
- ProjectE 等价交换
- Titan 泰坦（文件损坏，需重新下载）
- Avaritia 无尽贪婪
- 压缩木棍

---

## ❌ NeoForge / Quilt（未开始）

- NeoForge：复用 Forge 代码，调整 @Mod 包路径和注册方式
- Quilt：复用 Fabric 代码，适配 Quilt Loader API 差异

---

## ⚠️ 网络限制（下载受阻）

- CurseForge 直连失败（AccessDenied）
- Modrinth API 不通
- GitHub API 限流（403）
- 国内镜像站（mcpoly / minecarft / mcber）均无法批量下载 mod 文件

待下载：Xaero's Minimap、XRay、EXTREAL、Better Leaves、Compliance 64x、Low On Fire、Punchy!

## 🔧 已修复的关键问题

1. **JiJ 嵌套 jar 自动重写** — ModBytecodeRewriter 自动处理 META-INF/jars/*.jar
2. **嵌套 jar 签名清理** — 改 class 文件后删 SHA-256 签名
3. **嵌套 jar mixin 清理** — 删掉嵌套 jar 的 mixin 配置
4. **字符串常量点号重写** — 反射/Class.forName 用的点号格式类名
5. **手动路径映射**：
   - `net/minecraft/resources/ResourceLocation` → `net/minecraft/resources/Identifier`
   - `net/minecraft/client/gui/GuiGraphics` → `net/minecraft/client/gui/GuiGraphicsExtractor`
   - `net/minecraft/ResourceLocationException` → `net/minecraft/IdentifierException`
   - `net/minecraft/world/level/GameRules` → `net/minecraft/world/level/gamerules/GameRules`
   - `net/minecraft/world/level/Explosion` → `net/minecraft/world/level/ServerExplosion`

## 📊 映射数据

- 类映射：8,230 条
- 方法映射：40,865 条（按描述符精确匹配）
- 字段映射：37,430 条

## 📸 截图

- 零 mod 主菜单：`/home/user/forbric-263/screenshot-mainmenu.png`
- Carpet 主菜单：`/home/user/forbric-263/screenshot-carpet-mainmenu.png`
- 5 mod HUD 主菜单：`/home/user/forbric-263/screenshot-hud-mainmenu.png`
