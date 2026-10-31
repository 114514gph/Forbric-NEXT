package net.neoforbric.version;

import net.neoforbric.api.LoadContext;
import net.neoforbric.api.bukkit.MixedServerMode;
import net.neoforbric.common.NextLog;

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

		// 读取启动脚本注入的 -Dforbric.serverMode（pure / mixed）。
		// 这是纯服务端的分支：客户端启动路径（ClientBootstrap/Main）不走这里，因此不会被影响。
		MixedServerMode serverMode = MixedServerMode.fromSystemProperty();
		NextLog.info("ServerBootstrap: server mode = {}", serverMode);
		if (serverMode.isMixed()) {
			// 混合端：在 mod 体系就绪后，由可选的 PluginLoaderBridge 实现去加载 ./plugins 下的
			// Bukkit 插件。这里只做提示，真正的桥接实现由可选模块以 SPI 提供。
			NextLog.info("ServerBootstrap: mixed mode — Bukkit plugin bridge (PluginLoaderBridge) will be "
					+ "installed after mod construction; plugins dir = ./plugins");
		} else {
			NextLog.info("ServerBootstrap: pure mode — no Bukkit plugin bridge attached");
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
