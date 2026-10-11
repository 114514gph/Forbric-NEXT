# Forbric-NEXT

[简体中文](README.zh-CN.md) | **English**

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Java](https://img.shields.io/badge/java-17%2B-orange)
![MC](https://img.shields.io/badge/mc-1.18%20%7C%201.20%20%7C%201.21%20%7C%2026.x-green)
![Status](https://img.shields.io/badge/status-alpha-yellow)

Run Fabric, Forge, and NeoForge mods in one instance — without the loader fighting itself.

## Why

I spent a while hacking on [Forbric 0.3.0](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader)
trying to get it to co-load mods from three ecosystems. The idea was right, but the architecture
gradually turned into something I couldn't work with:

- The boot layer rebuilt one big dependency graph and sorted Forge mods together with Fabric mods,
  ignoring how Forge's own `ModSorter` actually works.
- Eight of the 35 "canary" modules were just hardcoded `if (modId.equals("carpet"))` patches
  to make one specific mod launch.
- The "sovereign kernel" still pulled build artifacts from the old loader's shell scripts.
- Half the lifecycle callbacks were empty stubs that nobody noticed.

So I stopped patching and started over. Forbric-NEXT is a clean module graph where each ecosystem
owns its own discovery, dependency resolution, and lifecycle. The boot layer only decides
*between* ecosystems — it never re-sorts a mod list that belongs to another loader.

## Quick start

Requires JDK 21+.

```bash
git clone https://github.com/114514gph/Forbric-NEXT.git
cd Forbric-NEXT
./gradlew build
./gradlew :forbric-next-launcher:run
```

That's it — no `bootstrap.sh`, no pre-built jars to download, no environment variables to set.
Maven goes through Aliyun mirrors, Gradle through Tencent.

## Project structure

```
Forbric-NEXT/
├── forbric-next-loader-api/     # the SPI: ModLoader, LoadPhase, EventBus, BootOrchestrator
├── forbric-next-common/         # shared utilities
├── forbric-next-fabric-api/     # minimal fabric-api event bridge + language adapter SPI
├── forbric-next-version/       # per-MC-version adapter (1.18 / 1.20 / 1.21 / 26.x skeletons)
├── forbric-next-fabric/         # Fabric ecosystem loader
├── forbric-next-forge/          # MinecraftForge ecosystem loader
├── forbric-next-neoforge/       # NeoForge ecosystem loader
├── forbric-next-boot/           # cross-ecosystem orchestrator
└── forbric-next-launcher/       # entry point
```

The key contract: the boot orchestrator calls `discover() → resolve() → runPhase()` on each
loader and nothing else. It doesn't know your mod IDs. It doesn't mix cross-ecosystem deps
into one graph. It doesn't inject per-mod special cases.

## Status

Early skeleton. Everything compiles, the 9-phase lifecycle runs end-to-end, and the
fabric-api event bridge fires correctly. What's not there yet:

- Real mod discovery (port from 0.3.0)
- Mixin wiring
- Actually launching Minecraft
- The 1.18 / 1.20 / 1.21 version adapters are interface stubs; only 26.x has a functional adapter

If you want to help, the next milestone is porting `FabricModDiscovery` and `FabricLoadOrder`
from 0.3.0 into `forbric-next-fabric`.

## Credits

Based on lessons learned from [Forbric 0.3.0 by Ray-T-r](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader).
Same goal, different architecture. This repo is a fork of that codebase but has been rewritten
from the module graph up — the old loader's classes are not carried over.

## License

Apache-2.0. See [LICENSE](LICENSE).
