# Forbric-NEXT

A next-generation multi-ecosystem Minecraft mod loader that runs **Fabric**, **MinecraftForge**,
and **NeoForge** mods side by side — without reimplementing each loader's internal ordering.

> **Status: early skeleton.** This is a clean redesign of Forbric 0.3.0, not a fork.
> It compiles, runs the 9-phase lifecycle across all three loaders, and ships a minimal
> fabric-api event bridge. Porting the discovery, metadata parsing, and mixin wiring is
> the next milestone.

## Why "NEXT"?

Forbric 0.3.0 proved the multi-loader concept but accumulated four structural problems
that made the codebase fragile:

1. **"Strong compat" overreach** — the boot layer rebuilt a unified dependency graph and
   sorted Forge mods with Fabric mods mixed in, ignoring Forge's native `ModSorter` semantics.
2. **Per-mod canary patches** — 8 of 35 canary modules were hardcoded workarounds for
   specific published mods (Carpet, Create, C2ME…).
3. **Build-pipeline coupling** — the "sovereign kernel" still consumed jars produced by
   the legacy forbric-loader shell scripts.
4. **Silent stubs** — lifecycle callbacks were empty, `getFile()` returned null, and
   `isDevelopmentEnvironment()` was hardcoded false.

Forbric-NEXT starts from a clean module graph where each ecosystem owns its discovery,
dependency resolution, and lifecycle. The boot layer only decides *between* ecosystems.

## Architecture

```
forbric-next-loader-api     SPI: ModLoader / LoadPhase / EventBus / BootOrchestrator
forbric-next-common         shared utilities
forbric-next-fabric-api     minimal fabric-api event bridge + language adapter SPI
forbric-next-fabric         Fabric ecosystem loader (native mod-id ordering)
forbric-next-forge          MinecraftForge ecosystem loader (BEFORE/AFTER edges only)
forbric-next-neoforge       NeoForge ecosystem loader
forbric-next-boot           cross-ecosystem orchestrator
forbric-next-launcher       entry point
```

### Core contract

The boot orchestrator calls only three methods on each loader:
`discover()` → `resolve()` → `runPhase(LoadPhase)`. It never re-sorts a loader's internal
mod list, never mixes cross-ecosystem dependencies into one topological graph, and never
injects per-mod special cases.

- **Fabric mods** run in mod-id lexicographic order (matching Fabric Loader native).
- **Forge/NeoForge mods** run in the order their own `ModSorter` produces (explicit
  BEFORE/AFTER edges only; mandatory `depends` is a presence requirement, not a sequence).
- **Cross-ecosystem deps** are presence checks, not ordering edges.

## Build

Requires JDK 21+.

```bash
./gradlew build
./gradlew :forbric-next-launcher:run
```

Maven repositories use Aliyun mirrors by default. Gradle wrapper uses Tencent Cloud mirror.

## License

Apache-2.0. See [LICENSE](LICENSE).
