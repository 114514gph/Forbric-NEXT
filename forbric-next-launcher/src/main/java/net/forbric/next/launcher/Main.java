package net.forbric.next.launcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import net.forbric.next.api.LoadContext;
import net.forbric.next.boot.DefaultBootOrchestrator;
import net.forbric.next.fabric.FabricModLoader;
import net.forbric.next.forge.ForgeModLoader;
import net.forbric.next.neoforge.NeoForgeModLoader;
import net.forbric.next.common.NextLog;
import net.forbric.next.fabricapi.event.ServerLifecycleEvents;
import net.forbric.next.fabricapi.language.LanguageAdapterRegistry;

/**
 * NeoForbric entry point.
 *
 * <p>This is the main class the game launch wrapper invokes. It wires up the three ecosystem
 * loaders and hands control to the {@link DefaultBootOrchestrator}.
 *
 * <p><b>Skeleton status.</b> The real launcher will parse JVM arguments, locate the game jar,
 * install the transforming classloader, and hand off to the game's main. For the skeleton
 * it demonstrates the boot sequence end-to-end, including the minimal fabric-api event bridge.
 */
public final class Main {

	public static void main(String[] args) {
		NextLog.info("NeoForbric 0.1.0-skeleton booting");

		Path gameDir = Paths.get(".").toAbsolutePath();
		Path modsDir = gameDir.resolve("mods");
		LoadContext context = new LoadContext(
				gameDir,
				modsDir,
				gameDir.resolve("patched-mc.jar"),
				LoadContext.Environment.CLIENT
		);

		// Demonstrate the fabric-api event bridge: a mod would call this from onInitialize().
		// 事件回调现在是 Consumer<XxxEvent>，用 event 占位（骨架阶段不读上下文）。
		ServerLifecycleEvents.SERVER_STARTED.register(event ->
				NextLog.info("  [demo listener] server started hook fired"));
		ServerLifecycleEvents.SERVER_STOPPED.register(event ->
				NextLog.info("  [demo listener] server stopped hook fired"));
		NextLog.info("Registered demo server lifecycle listeners (language adapters available: {})",
				LanguageAdapterRegistry.has("default") ? "default (Java)" : "none");

		DefaultBootOrchestrator orchestrator = new DefaultBootOrchestrator();
		FabricModLoader fabric = new FabricModLoader();
		// Between-ecosystem scan order: NeoForge first, then MinecraftForge, then Fabric.
		// Each loader owns its internal ordering; this list only decides scan priority.
		orchestrator.installLoaders(List.of(
				new NeoForgeModLoader(),
				new ForgeModLoader(),
				fabric
		));

		orchestrator.discover(context);
		orchestrator.resolve();
		try {
			orchestrator.runLifecycle();
		} catch (Exception e) {
			NextLog.error("Lifecycle failed: {}", e.toString());
			System.exit(1);
		}

		// Shutdown: let the Fabric loader fire stopping/stopped events.
		fabric.onShutdown();

		NextLog.info("NeoForbric skeleton boot complete (handoff to game main is not implemented yet)");
	}
}
