package net.forbric.next.fabric;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;

/**
 * Quilt ecosystem loader.
 *
 * <p>Quilt is a fork of Fabric, so the mod format is highly compatible:
 * <ul>
 *   <li>Both use {@code fabric.mod.json} (Quilt mods can also have {@code quilt.mod.json})</li>
 *   <li>Both use entrypoints (main, client, server) with the same format</li>
 *   <li>Both use mixins the same way</li>
 *   <li>Differences: Quilt has its own loader, some API differences in registration</li>
 * </ul>
 *
 * <p>This loader reuses FabricModDiscovery for mod scanning,
 * but marks discovered mods as QUILT ecosystem when quilt.mod.json is present.
 */
public class QuiltModLoader implements ModLoader {

    private final List<ModCandidate> discovered = new ArrayList<>();

    @Override
    public Ecosystem ecosystem() {
        return Ecosystem.QUILT;
    }

    @Override
    public List<ModCandidate> discover(LoadContext context) {
        NextLog.info("QuiltModLoader: scanning {} for quilt.mod.json", context.modsDir());
        List<ModCandidate> out = new ArrayList<>();
        Path modsDir = context.modsDir();
        if (modsDir == null || !Files.isDirectory(modsDir)) return out;

        try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : jars) {
                // Use QuiltModDetector to check if this is a Quilt mod
                List<ModCandidate> quiltMods = QuiltModDetector.detect(jar);
                for (ModCandidate m : quiltMods) {
                    out.add(m);
                    NextLog.info("  Found Quilt mod: {} {} ({})", m.id(), m.version(), jar.getFileName());
                }
            }
        } catch (IOException e) {
            NextLog.error("QuiltModLoader: scan failed: {}", e.toString());
        }
        return out;
    }

    @Override
    public void resolve(List<ModCandidate> candidates, LoadContext context) {
        discovered.clear();
        // Quilt uses the same dependency ordering as Fabric
        discovered.addAll(FabricLoadOrder.sort(candidates));
        NextLog.info("QuiltModLoader: resolved {} quilt mod(s)", discovered.size());
    }

    @Override
    public void runPhase(LoadPhase phase) {
        switch (phase) {
            case CONSTRUCT -> NextLog.info("QuiltModLoader: CONSTRUCT - {} mods", discovered.size());

            case REGISTRY -> {
                NextLog.info("QuiltModLoader: REGISTRY - firing entrypoints");
                // Quilt entrypoints work the same as Fabric
                FabricEntrypointExecutor.execute(discovered, "main");
            }

            case SIDED_SETUP -> {
                NextLog.info("QuiltModLoader: SIDED_SETUP (client)");
                FabricEntrypointExecutor.execute(discovered, "client");
            }

            case LOAD_COMPLETE -> {
                NextLog.info("QuiltModLoader: LOAD_COMPLETE - {} mods loaded", discovered.size());
            }

            default -> { /* no-op */ }
        }
    }

    public List<ModCandidate> discovered() { return discovered; }
}
