# Forbric-NEXT

[简体中文](README.zh-CN.md) | **English**

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Java](https://img.shields.io/badge/java-17%2B-orange)
![MC](https://img.shields.io/badge/mc-1.18%20%7C%201.20%20%7C%201.21%20%7C%2026.x-green)
![Status](https://img.shields.io/badge/status-alpha-yellow)

One Minecraft instance, running Fabric, Forge, NeoForge, and Quilt mods together.

---

## Why does this exist

I spent a while hacking on [Forbric 0.3.0](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader) trying to get it to co-load mods from all three ecosystems. The idea was right, but by the time 0.3.0 shipped the architecture had accumulated problems I couldn't fix without rewriting:

- **Merged-base architecture debt.** Forge and NeoForge both patch the same Minecraft classes, so Forbric merged them into one giant bytecode jar. This moved method bodies around, which silently broke fabric-api mixins that targeted those methods. The fix was a growing list of suppressed mixins (`MergedBaseMixinCompat`) — each entry was a feature sacrificed to make boot stop crashing. The code comments themselves say "this list is a debt, not a design."

- **Forced unification of mod ordering.** Instead of letting Fabric Loader sort Fabric mods and Forge's `ModSorter` sort Forge mods, Forbric threw every mod into one big Kahn topological graph and re-sorted it. This meant a Fabric mod's dependencies could push a Forge mod around in the load order — and vice versa. The sort was also stricter than Forge's native one (it treated mandatory `depends` as ordering edges, which Forge doesn't).

- **Per-mod canary patches.** 35 "canary" modules existed to make specific mods boot. Eight of them were hardcoded `if (modId.equals("carpet"))`-style special cases — not general compatibility layers, but patches bolted on for one test mod at a time.

- **Silent exception swallowing.** Hundreds of `catch` blocks either had no logging at all, or logged at debug level. A mod's entrypoint could fail, a mixin config could refuse to apply, an event listener could throw — and nobody would know. The game would just continue as if nothing happened.

- **Old loader still propping up the new one.** The "sovereign kernel" still pulled build artifacts from the old forbric-loader's shell scripts, and a 3800-line bytecode transformer existed purely to rename old class names to new ones at runtime.

So I stopped patching and started over. Forbric-NEXT is a clean module graph where each ecosystem owns its own discovery, dependency resolution, and lifecycle. The boot layer only decides *between* ecosystems — it never re-sorts a mod list that belongs to another loader.

## How it's different from Connector / Kilt

You've probably heard of [Sinytra Connector](https://github.com/Sinytra/Connector) or [Kilt](https://github.com/FabricMC/fabric-loader) (the Fabric-on-NeoForge layer). They're translation mods you install on top of an existing loader — they reimplement Fabric API inside Forge, or Forge's event bus inside Fabric. That works, but it means one ecosystem is always the "real" host and the other is running through a shim.

Forbric-NEXT is the loader itself. There's no host loader being translated. Each ecosystem's mod discovery, metadata parsing, entrypoint ordering, and mixin wiring runs through its own module. The boot orchestrator calls `discover() → resolve() → runPhase()` on each loader and nothing else. It doesn't know your mod IDs. It doesn't mix cross-ecosystem dependencies into one graph.

**That said:** Connector is mature and works for most people. Forbric-NEXT is an alpha skeleton. If Connector already runs your modpack, use Connector. Forbric-NEXT is for people who want to understand how a three-ecosystem loader actually works under the hood, or who want to build a loader that doesn't force one ecosystem to be the host.

## Installation

### Client (GUI installer)

Requires **JRE 17 or later**.

1. Download `forbric-next-installer.jar` from the [latest release](https://github.com/114514gph/Forbric-NEXT/releases/latest).
2. Run it:
   ```bash
   java -jar forbric-next-installer.jar
   ```
3. Pick your Minecraft version from the dropdown (1.18.2 through 26.3).
4. Verify the `.minecraft` path — it auto-detects the default location, but use **Browse…** if your launcher stores it elsewhere.
5. Click **Install**. The installer creates a `{mc-version}-forbric-next` folder in your `versions/` directory.
6. Open your launcher. The new version should appear in the version list. Launch it like any other version.

To uninstall: click **Uninstall** in the installer, or just delete the `{mc-version}-forbric-next` folder from `versions/`.

### Dedicated server (CLI installer)

Requires **JRE 17 or later**.

```bash
# Pure Forbric server (default)
java -jar forbric-next-server-installer.jar --mc 1.21.1 --dir /opt/mc-server --accept-eula

# Mixed server (Forbric + Bukkit plugins, Mohist/Arclight style)
java -jar forbric-next-server-installer.jar --mc 1.21.1 --dir /opt/mc-server --server-mode mixed --accept-eula
```

| Flag | What it does |
|------|-------------|
| `--mc <version>` | Which Minecraft version to install for |
| `--dir <path>` | Target server directory |
| `--server-mode pure\|mixed` | Pure mod server, or mixed with Bukkit plugin support (default: pure) |
| `--accept-eula` | Auto-writes `eula=true` (you still agree to Mojang's EULA) |
| `--help` | Show all options |

After install, edit `start.sh` / `start.bat` to adjust JVM flags, then run it. A `.forbric-next.toml` config file is written for you to customize load behavior.

## Where do I put mods

**All mods go in the same `mods` folder.** Forbric-NEXT doesn't sort them by ecosystem — drop Fabric, Forge, NeoForge, and Quilt mods all in together.

The exact path depends on your launcher:
- Version-isolated launchers (PCL2, HMCL): `.minecraft/versions/{mc-version}-forbric-next/mods/`
- Non-isolated: your `.minecraft/mods/` (the same one other versions use)

**Quilt mods** are auto-detected: if a jar has `quilt.mod.json`, it's loaded as Quilt; if it only has `fabric.mod.json`, it's loaded as Fabric. You don't need to tell the installer which is which.

**One version per mod.** If a mod exists in both Fabric and Forge flavors, only put one in the folder. Forbric won't complain, but it also won't run both.

## Current status

This is **alpha**. It compiles, the lifecycle runs end-to-end, and the event bridge fires correctly — but it doesn't actually launch Minecraft yet.

| What works | What's still a skeleton |
|------------|------------------------|
| 9-phase lifecycle across all loaders | Real mod discovery (port from 0.3.0) |
| Fabric / Forge / NeoForge / Quilt mod detection | Mixin wiring |
| Minimal fabric-api event bridge (Server/Client lifecycle events) | Actual Minecraft launch |
| Version adapter SPI (1.18 / 1.20 / 1.21 / 26.x interfaces) | Functional version adapters (only 26.x marked as such) |
| Bukkit mixed-server SPI (interface defined) | Bukkit plugin loader implementation |
| Client GUI installer + server CLI installer | Real game file download (skeleton creates folder structure only) |
| Hard dependency validation (fail boot on missing mandatory deps) | |

**Supported MC versions** (installer catalog):

| Version | Forge | NeoForge | Fabric API |
|---------|-------|----------|------------|
| 1.18.2 | 40.3.12 | — | 0.14.24 |
| 1.19.2 | 43.5.2 | — | 0.14.25 |
| 1.20.4 | 49.2.9 | 20.4.0-beta | 0.15.10 |
| 1.21.1 | 52.1.16 | 21.1.45 | 0.16.14+1.21.1 |
| 1.21.11 | 61.2.1 | 21.11.40-beta | 0.16.14+1.21.11 |
| 26.3 | — | 26.3.0.76 | 0.155.2+26.3 |

Fabric Loader 26.1.2 is the baseline (it's versioned independently of MC).

## Troubleshooting

| What you see | What to do |
|--------------|------------|
| Installer says "requires Java 17+" | Install a newer JRE. If you already run Minecraft, you probably have one — point `JAVA_HOME` at it. |
| Server boot stops with "Unmet mandatory mod dependencies" | A mod needs a prerequisite that isn't installed. The error lists exactly what's missing. Install it, or add `-Dforbric.compatibilityPolicy=continue` to skip. |
| A mod loads but doesn't do anything | Check `.forbric-next/load-report.txt` next to your mods folder. Mods that failed to initialize are listed there. |
| Mixed server mode errors about Bukkit | The Bukkit SPI is interface-only. Plugin loading isn't implemented yet — mixed mode currently just sets the JVM flag. |
| Quilt mod not detected | Make sure the jar has `quilt.mod.json` in its root. Some "Quilt" mods are actually Fabric mods with Quilt compatibility — they'll load as Fabric. |

## Building from source

Requires **JDK 21**.

```bash
git clone https://github.com/114514gph/Forbric-NEXT.git
cd Forbric-NEXT
./gradlew build
```

Maven goes through Aliyun mirrors, Gradle through Tencent — no proxy setup needed if you're in China.

### Adding a new MC version

1. Create a class in `forbric-next-version/.../impl/` implementing `VersionAdapter`.
2. List the class names, event constants, and mapping scheme for that version.
3. **Don't guess class names.** Use [Linkie](https://linkie.shedaniel.dev) to look up cross-version mappings. For 1.19.4+, Mojang publishes official mappings (the game jar isn't fully obfuscated anymore) — decompile with Vineflower to confirm exact names.
4. Register your adapter in `VersionAdapterRegistry.initializeBuiltins()`.

### Extending the SPI

- New loader ecosystem: implement `ModLoader` in `forbric-next-loader-api`, register in `forbric-next-boot`.
- New event: add a method to the appropriate bridge interface in `forbric-next-fabric-api`.
- New server mode: extend `MixedServerMode` in `forbric-next-loader-api/.../bukkit/`.

## Credits

Based on lessons learned from [Forbric 0.3.0 by Ray-T-r](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader). Same goal — run all three mod ecosystems in one instance — but a different architecture. This repo is a fork of that codebase and has been rewritten from the module graph up; the old loader's class structure isn't carried over.

Thanks to the Fabric, Forge, NeoForge, and Quilt teams for making the mods that make this whole thing worth attempting.

## License

Apache-2.0. See [LICENSE](LICENSE).
