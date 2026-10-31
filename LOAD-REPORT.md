# Forbric-NEXT Load Report

MC 26.3 客户端跨版本兼容加载测试报告
测试时间：2026-10-31

## ✅ 已验证通过

### Fabric Mods

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

## ⚠️ 未测试 / 未下载

### 网络限制
- CurseForge 直连失败（AccessDenied）
- Modrinth API 不通
- GitHub API 限流（403）
- 仅 GitHub.com 网页可访问，无法批量下载 release 文件

### 未下载的 Fabric Mods

| Mod | 原因 |
|-----|------|
| Xaero's Minimap | 下载渠道不可用 |
| XRay | 下载渠道不可用 |
| Carpet TIS Addition | 文件已在 mods-bak/，未测试 |

### Forge Mods（架构不兼容）

**当前加载器是 Fabric Loader (KnotClient)，无法加载 Forge mod。**

Forbric-NEXT 的设计目标是多系兼容，但当前阶段只实现了 Fabric 加载层。Forge mod 需要 Forge/NeoForge Loader 才能运行，不在本次测试范围内。

| Mod | 备注 |
|-----|------|
| Create 机械动力 | Forge mod，需 Forge Loader |
| Valkyrien Skies 瓦尔基里 | Forge mod，需 Forge Loader |
| Farmer's Delight 农夫乐事 | Forge mod，需 Forge Loader |
| ProjectE 等价交换 | Forge mod，需 Forge Loader |
| Titan 泰坦 | Forge mod，文件在 mods-bak/ |
| Avaritia 无尽贪婪 | Forge mod，需 Forge Loader |
| 压缩木棍 | Forge mod，需 Forge Loader |

### 未下载的 Resource Packs

| Pack | 原因 |
|------|------|
| EXTREAL | 下载渠道不可用 |
| Better Leaves | 下载渠道不可用 |
| Compliance 64x | 下载渠道不可用 |
| Low On Fire | 下载渠道不可用 |
| Punchy! | 下载渠道不可用 |

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
