# NeoForbric on MC 26.3 — Integration Status

## 2026-10-29 Update

### What works
- Fabric Loader 0.16.10 boots on MC 26.3 (Java 25 required)
- **Version check bypass**: rewriting `depends.minecraft` in fabric.mod.json
  lets 59 mods (Carpet + Fabric API + MaLiLib + MiniHUD + Tweakeroo + Litematica)
  past Fabric Loader's compatibility check
- Xvfb virtual display starts (GLX works, window opens)
- Mod discovery: all 6 top-level jars detected with 58 nested sub-mods

### What's blocking (real bytecode remapping needed)
- Fabric API 0.116.17 references `net/minecraft/class_2960` (old intermediary name)
- MC 26.3 uses different obfuscated names — the old mod bytecode has stale references
- Error: `NoClassDefFoundError: net/minecraft/class_2960`
- This is exactly what RuntimeRemapper is designed to fix

### Next steps
1. Generate full 1.21.1→26.3 intermediary mappings (class_XXXX → new names)
2. Feed through RuntimeRemapper to rewrite mod jar bytecode
3. Re-run client, should load Fabric API without NoClassDefFoundError
