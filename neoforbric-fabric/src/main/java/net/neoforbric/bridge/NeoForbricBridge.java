package net.neoforbric.bridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.neoforbric.common.NextLog;
import net.neoforbric.forge.ForgeModLoader;
import net.neoforbric.api.LoadContext;
import net.neoforbric.api.LoadPhase;
import net.neoforbric.api.ModCandidate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Fabric bridge entrypoint - runs NeoForbric's cross-loader runtime
 * inside the Fabric launch sequence.
 */
public class NeoForbricBridge implements PreLaunchEntrypoint, ModInitializer {

    private final ForgeModLoader forgeLoader = new ForgeModLoader();
    private List<ModCandidate> discovered = List.of();

    @Override
    public void onPreLaunch() {
        NextLog.info("=== NeoForbric Bridge: PreLaunch ===");

        Path gameDir = Paths.get(".").toAbsolutePath();
        Path modsDir = gameDir.resolve("mods");

        if (!java.nio.file.Files.isDirectory(modsDir)) {
            NextLog.warn("NeoForbric: mods dir not found");
            return;
        }

        LoadContext context = new LoadContext(
                gameDir,
                modsDir,
                gameDir.resolve("patched-mc.jar"),
                LoadContext.Environment.CLIENT
        );

        // Discover Forge mods
        NextLog.info("Scanning for Forge mods...");
        discovered = forgeLoader.discover(context);
        NextLog.info("Found {} Forge mod(s)", discovered.size());
        for (ModCandidate m : discovered) {
            NextLog.info("  - {} v{} ({})", m.id(), m.version(), m.ecosystem());
        }

        // Run CONSTRUCT phase
        if (!discovered.isEmpty()) {
            NextLog.info("Running Forge CONSTRUCT phase...");
            forgeLoader.resolve(discovered, context);
            try {
                forgeLoader.runPhase(LoadPhase.CONSTRUCT);
            } catch (Exception e) {
                NextLog.error("Forge CONSTRUCT error: {}", e.toString());
            }
            NextLog.info("Forge CONSTRUCT complete");
        }

        NextLog.info("=== NeoForbric Bridge: PreLaunch done ===");
    }

    @Override
    public void onInitialize() {
        NextLog.info("=== NeoForbric Bridge: ModInitializer ===");

        try {
            if (!discovered.isEmpty()) {
                forgeLoader.runPhase(LoadPhase.REGISTRY);
                NextLog.info("Forge REGISTRY phase complete");

                forgeLoader.runPhase(LoadPhase.COMMON_SETUP);
                NextLog.info("Forge COMMON_SETUP phase complete");
            }
        } catch (Exception e) {
            NextLog.error("Forge lifecycle error: {}", e.toString());
        }

        NextLog.info("=== NeoForbric Bridge: Initialize done ===");
    }
}
