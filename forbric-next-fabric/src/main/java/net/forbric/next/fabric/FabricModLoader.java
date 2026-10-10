package net.forbric.next.fabric;

import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;
import net.forbric.next.fabricapi.event.ClientLifecycleEvents;
import net.forbric.next.fabricapi.event.ServerLifecycleEvents;

/**
 * Fabric ecosystem loader.
 *
 * <p><b>Skeleton status.</b> The discovery and dependency-resolution logic from Forbric 0.3.0's
 * {@code KernelFabricEcosystem} and {@code FabricModDiscovery} will be ported here. The key
 * architectural contract this stub enforces:
 * <ul>
 *   <li>Fabric mod order is by mod-id lexicographic (matching Fabric Loader native),
 *       <em>not</em> by a cross-ecosystem topological sort.</li>
 *   <li>Entrypoint groups run preLaunch → main → client/server in that fixed order.</li>
 *   <li>No Fabric mod is ever pulled into a Forge @Mod construction graph.</li>
 * </ul>
 *
 * <p>This loader also drives the minimal fabric-api event bridge
 * ({@link ServerLifecycleEvents}, {@link ClientLifecycleEvents}): when the boot orchestrator
 * reaches {@link LoadPhase#GAME_STARTED}, the server-started listeners fire; at shutdown,
 * stopping/stopped listeners fire.
 */
public class FabricModLoader implements ModLoader {

	private final List<ModCandidate> discovered = new ArrayList<>();

	@Override
	public Ecosystem ecosystem() {
		return Ecosystem.FABRIC;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		NextLog.info("FabricModLoader: scanning {} for fabric.mod.json", context.modsDir());
		// TODO: port FabricModDiscovery from forbric-kernel.
		return List.of();
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		discovered.clear();
		discovered.addAll(candidates);
		// TODO: port FabricLoadOrder.byModId (native Fabric ordering, NOT a cross-ecosystem Kahn sort).
		NextLog.info("FabricModLoader: resolved {} fabric mod(s); ordering by mod-id lexicographic",
				discovered.size());
	}

	@Override
	public void runPhase(LoadPhase phase) {
		switch (phase) {
			case PRE_LAUNCH -> NextLog.info("FabricModLoader: running preLaunch entrypoints");
			case REGISTRY -> NextLog.info("FabricModLoader: running main entrypoints in registry window");
			case SIDED_SETUP -> {
				NextLog.info("FabricModLoader: running client/server entrypoints");
				// Client lifecycle event: mods registered on CLIENT_STARTED fire here.
				ClientLifecycleEvents.CLIENT_STARTED.invoker().run();
			}
			case GAME_STARTED -> {
				NextLog.info("FabricModLoader: firing server lifecycle events");
				ServerLifecycleEvents.SERVER_STARTING.invoker().run();
				ServerLifecycleEvents.SERVER_STARTED.invoker().run();
			}
			default -> { /* no-op for this phase */ }
		}
	}

	@Override
	public void onShutdown() {
		NextLog.info("FabricModLoader: firing server stopping/stopped events");
		ServerLifecycleEvents.SERVER_STOPPING.invoker().run();
		ServerLifecycleEvents.SERVER_STOPPED.invoker().run();
		ClientLifecycleEvents.CLIENT_STOPPING.invoker().run();
	}
}
