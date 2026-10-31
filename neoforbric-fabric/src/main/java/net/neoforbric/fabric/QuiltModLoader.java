package net.neoforbric.fabric;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.api.LoadContext;
import net.neoforbric.api.LoadPhase;
import net.neoforbric.api.ModCandidate;
import net.neoforbric.api.ModLoader;
import net.neoforbric.common.NextLog;

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
                if (QuiltModDetector.isQuiltMod(jar)) {
                    try {
                        ModCandidate m = QuiltModDetector.toCandidate(jar);
                        out.add(m);
                        NextLog.info("  Found Quilt mod: {} {} ({})", m.id(), m.version(), jar.getFileName());
                    } catch (Exception e) {
                        NextLog.warn("QuiltModLoader: failed to read {}: {}", jar.getFileName(), e.getMessage());
                    }
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
        discovered.addAll(candidates); // simple ordering for now
        NextLog.info("QuiltModLoader: resolved {} quilt mod(s)", discovered.size());
    }

    @Override
    public void runPhase(LoadPhase phase) {
        switch (phase) {
            case CONSTRUCT -> NextLog.info("QuiltModLoader: CONSTRUCT - {} mods", discovered.size());

            case REGISTRY -> {
                NextLog.info("QuiltModLoader: REGISTRY - {} mods", discovered.size());
                // Entrypoint execution reuses Fabric logic (TODO: instantiate properly)
            }

            case SIDED_SETUP -> {
                NextLog.info("QuiltModLoader: SIDED_SETUP (client)");
            }

            case LOAD_COMPLETE -> {
                NextLog.info("QuiltModLoader: LOAD_COMPLETE - {} mods loaded", discovered.size());
            }

            default -> { /* no-op */ }
        }
    }

    public List<ModCandidate> discovered() { return discovered; }
}
