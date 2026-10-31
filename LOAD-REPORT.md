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

**5 个 mod 同时加载，MC 26.3 主菜单正常渲染。**

### Resource Packs

| Pack | 版本 | 状态 | 备注 |
|------|------|------|------|
| Faithful 32x | latest | ✅ 结构正常 | pack_format 34 |
| Fresh Animations | 1.11.1 | ✅ 结构正常 | pack_format 15-75 |

## ⚠️ 待测试

| Mod | 类型 | 状态 | 备注 |
|-----|------|------|------|
| Carpet TIS Addition | Fabric | ⏳ 未测试 | 文件在 mods-bak/ |
| Titan | Forge 1.20.4 | ⏳ 未测试 | 文件在 mods-bak/ |
| Xaero's Minimap | Fabric | ❌ 未下载 | CurseForge 直连失败 |
| XRay | Fabric | ❌ 未下载 | CurseForge 直连失败 |
| Create | Forge | ❌ 未下载 | |
| Valkyrien Skies | Forge | ❌ 未下载 | |
| Farmer's Delight | Forge | ❌ 未下载 | |
| ProjectE | Forge | ❌ 未下载 | |
| Avaritia | Forge | ❌ 未下载 | |
| 压缩木棍 | Forge | ❌ 未下载 | |

### 待测试 Resource Packs

| Pack | 状态 |
|------|------|
| EXTREAL | ❌ 未下载 |
| Better Leaves | ❌ 未下载 |
| Compliance 64x | ❌ 未下载 |
| Low On Fire | ❌ 未下载 |
| Punchy! | ❌ 未下载 |

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
