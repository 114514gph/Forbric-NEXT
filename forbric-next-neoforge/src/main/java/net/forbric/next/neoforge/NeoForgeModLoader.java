package net.forbric.next.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;

/**
 * NeoForge ecosystem loader.
 *
 * <p><b>Skeleton status.</b> Same contract as {@link ForgeModLoader}: NeoForge @Mod construction
 * order uses explicit BEFORE/AFTER edges only; mandatory depends is presence, not ordering.
 * NeoForge has its own mod-bus / game-bus split that this loader will own.
 */
public class NeoForgeModLoader implements ModLoader {

	private final List<ModCandidate> discovered = new ArrayList<>();

	@Override
	public Ecosystem ecosystem() {
		return Ecosystem.NEOFORGE;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		NextLog.info("NeoForgeModLoader: scanning {} for META-INF/neoforge.mods.toml", context.modsDir());
		return List.of();
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		discovered.clear();
		discovered.addAll(candidates);
		NextLog.info("NeoForgeModLoader: resolved {} neoforge mod(s); ordering by explicit BEFORE/AFTER",
				discovered.size());
	}

	@Override
	public void runPhase(LoadPhase phase) {
		switch (phase) {
			case CONSTRUCT -> NextLog.info("NeoForgeModLoader: constructing mod classes");
			case REGISTRY -> NextLog.info("NeoForgeModLoader: firing RegisterEvent");
			case COMMON_SETUP -> NextLog.info("NeoForgeModLoader: FMLCommonSetupEvent");
			case SIDED_SETUP -> NextLog.info("NeoForgeModLoader: FMLClientSetupEvent / FMLDedicatedServerSetupEvent");
			case IMC_ENQUEUE -> NextLog.info("NeoForgeModLoader: InterModEnqueueEvent");
			case IMC_PROCESS -> NextLog.info("NeoForgeModLoader: InterModProcessEvent");
			case LOAD_COMPLETE -> NextLog.info("NeoForgeModLoader: FMLLoadCompleteEvent");
			default -> { /* no-op */ }
		}
	}
}
