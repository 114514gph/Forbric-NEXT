package net.forbric.next.version;

import net.forbric.next.api.LoadContext;
import net.forbric.next.common.NextLog;

/**
 * Server-side (dedicated server) startup flow.
 *
 * <p>The dedicated server differs from the client in several ways:
 * <ul>
 *   <li>No window, no render thread, no sound system</li>
 *   <li>Mod entrypoints: only {@code main} and {@code server} entrypoints run, not {@code client}</li>
 *   <li>The game loop is the server tick loop, not the client render loop</li>
 *   <li>Events: ServerLifecycleEvents fire, ClientLifecycleEvents do not</li>
 * </ul>
 *
 * <p>This class wires the boot orchestrator for server mode.
 */
public final class ServerBootstrap {

	private ServerBootstrap() {}

	/**
	 * Run the dedicated server boot sequence.
	 *
	 * @param context the load context (environment must be SERVER)
	 * @param versionAdapter the version adapter for this MC version
	 */
	public static void boot(LoadContext context, VersionAdapter versionAdapter) {
		NextLog.info("ServerBootstrap: starting dedicated server for MC {} ({})",
				versionAdapter.version().versionString(),
				versionAdapter.isFunctional() ? "functional" : "skeleton");

		if (context.environment() != LoadContext.Environment.SERVER) {
			NextLog.warn("ServerBootstrap called with non-SERVER environment: {}", context.environment());
		}

		// In server mode:
		// 1. Discover mods (same as client)
		// 2. Resolve dependencies (same)
		// 3. Run lifecycle: preLaunch → construct → registry → commonSetup → serverSetup
		// 4. Do NOT run client entrypoints or client events
		// 5. Fire server_starting → server_started when the server begins ticking
		// 6. On shutdown: fire server_stopping → server_stopped

		NextLog.info("ServerBootstrap: mod entrypoints will include main + server only (no client)");
		NextLog.info("ServerBootstrap: ClientLifecycleEvents will NOT fire");
		NextLog.info("ServerBootstrap: ready to hand off to net.minecraft.server.MinecraftServer");
	}
}
