package net.forbric.next.forge;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.ModsToml;
import net.forbric.next.api.util.ModDependencySorter;
import net.forbric.next.common.NextLog;

/**
 * MinecraftForge ecosystem loader.
 *
 * <p>Full implementation:
 * <ul>
 *   <li>Scans mods/ for jars with META-INF/mods.toml</li>
 *   <li>Discovers @Mod annotated classes via bytecode scanning</li>
 *   <li>Instantiates mod classes with isolated classloaders</li>
 *   <li>Runs lifecycle: Construction → Registry → Setup → SidedSetup → IMC → LoadComplete</li>
 *   <li>ForgeEventBus dispatches events to @SubscribeEvent handlers</li>
 * </ul>
 */
public class ForgeModLoader implements ModLoader {

    private final List<ModCandidate> discovered = new ArrayList<>();
    private final ForgeModRuntime runtime = new ForgeModRuntime();

    @Override
    public Ecosystem ecosystem() {
        return Ecosystem.MINECRAFTFORGE;
    }

    @Override
    public List<ModCandidate> discover(LoadContext context) {
        NextLog.info("ForgeModLoader: scanning {} for META-INF/mods.toml", context.modsDir());
        List<ModCandidate> out = new ArrayList<>();
        Path modsDir = context.modsDir();
        if (modsDir == null || !Files.isDirectory(modsDir)) return out;

        try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : jars) {
                var mods = ModsToml.readFromJar(jar, "META-INF/mods.toml");
                if (mods.isEmpty()) continue; // not a Forge mod, skip

                for (ModsToml.Mod m : mods.get()) {
                    out.add(new ModCandidate(m.modId(), m.version(),
                        Ecosystem.MINECRAFTFORGE, jar, List.of()));
                    NextLog.info("  Found Forge mod: {} {} ({})", m.modId(), m.version(), jar.getFileName());
                }
            }
        } catch (IOException e) {
            NextLog.error("ForgeModLoader: scan failed: {}", e.toString());
        }
        return out;
    }

    @Override
    public void resolve(List<ModCandidate> candidates, LoadContext context) {
        discovered.clear();
        // Forge ordering: BEFORE/AFTER edges determine construction order
        // For now, sort by modId for stability
        discovered.addAll(ModDependencySorter.sort(candidates, Map.of()));
        NextLog.info("ForgeModLoader: resolved {} forge mod(s)", discovered.size());
    }

    @Override
    public void runPhase(LoadPhase phase) {
        switch (phase) {
            case PRE_LAUNCH -> NextLog.debug("ForgeModLoader: PRE_LAUNCH (no-op)");

            case CONSTRUCT -> {
                NextLog.info("ForgeModLoader: CONSTRUCT - loading {} mods", discovered.size());
                for (ModCandidate c : discovered) {
                    runtime.loadMod(c);
                }
                runtime.fireConstructionEvent();
            }

            case REGISTRY -> {
                NextLog.info("ForgeModLoader: REGISTRY - firing register events");
                runtime.fireRegistryEvents();
            }

            case COMMON_SETUP -> {
                NextLog.info("ForgeModLoader: COMMON_SETUP");
                runtime.fireSetupEvent();
            }

            case SIDED_SETUP -> {
                NextLog.info("ForgeModLoader: SIDED_SETUP (client)");
                runtime.fireSidedSetupEvent(true);
            }

            case IMC_ENQUEUE -> {
                NextLog.info("ForgeModLoader: IMC_ENQUEUE");
                runtime.fireInterModEnqueueEvent();
            }

            case IMC_PROCESS -> {
                NextLog.info("ForgeModLoader: IMC_PROCESS");
                runtime.fireInterModProcessEvent();
            }

            case LOAD_COMPLETE -> {
                NextLog.info("ForgeModLoader: LOAD_COMPLETE");
                runtime.fireLoadCompleteEvent();
                NextLog.info("ForgeModLoader: {} mods loaded, {} event handlers, {} registered entries",
                    runtime.modCount(), runtime.handlerCount(), runtime.registeredCount());
            }

            case GAME_STARTED -> NextLog.debug("ForgeModLoader: GAME_STARTED (no-op)");
        }
    }

    public ForgeModRuntime runtime() { return runtime; }
}
