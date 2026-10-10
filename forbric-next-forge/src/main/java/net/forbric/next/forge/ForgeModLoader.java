package net.forbric.next.forge;

import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;

/**
 * MinecraftForge ecosystem loader.
 *
 * <p><b>Skeleton status.</b> The key contract this stub enforces (fixing 0.3.0's "strong compat" bug):
 * <ul>
 *   <li>Forge @Mod construction order uses Forge-native semantics: explicit BEFORE/AFTER edges only.
 *       Mandatory {@code depends} is a presence requirement, <em>not</em> an ordering edge.</li>
 *   <li>No Fabric mod is ever included in the Forge construction graph.</li>
 *   <li>Forge lifecycle phases run: Construct → CommonSetup → SidedSetup → Registration →
 *       IMC Enqueue → IMC Process → LoadComplete.</li>
 * </ul>
 */
public class ForgeModLoader implements ModLoader {

	private final List<ModCandidate> discovered = new ArrayList<>();

	@Override
	public Ecosystem ecosystem() {
		return Ecosystem.MINECRAFTFORGE;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		NextLog.info("ForgeModLoader: scanning {} for META-INF/mods.toml", context.modsDir());
		return List.of();
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		discovered.clear();
		discovered.addAll(candidates);
		// TODO: port Forge ModSorter semantics (BEFORE/AFTER edges only, NOT mandatory-depends edges).
		NextLog.info("ForgeModLoader: resolved {} forge mod(s); ordering by explicit BEFORE/AFTER",
				discovered.size());
	}

	@Override
	public void runPhase(LoadPhase phase) {
		switch (phase) {
			case CONSTRUCT -> NextLog.info("ForgeModLoader: constructing @Mod classes");
			case REGISTRY -> NextLog.info("ForgeModLoader: firing RegisterEvent");
			case COMMON_SETUP -> NextLog.info("ForgeModLoader: FMLCommonSetupEvent");
			case SIDED_SETUP -> NextLog.info("ForgeModLoader: FMLClientSetupEvent / FMLDedicatedServerSetupEvent");
			case IMC_ENQUEUE -> NextLog.info("ForgeModLoader: InterModEnqueueEvent");
			case IMC_PROCESS -> NextLog.info("ForgeModLoader: InterModProcessEvent");
			case LOAD_COMPLETE -> NextLog.info("ForgeModLoader: FMLLoadCompleteEvent");
			default -> { /* no-op */ }
		}
	}
}
