package net.forbric.next.boot;

import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.BootOrchestrator;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;

/**
 * Default boot orchestrator.
 *
 * <p><b>Critical invariant (regression guard against Forbric 0.3.0):</b> this class never
 * inspects the internal mod lists of a {@link ModLoader}. It calls {@link ModLoader#discover},
 * {@link ModLoader#resolve}, and {@link ModLoader#runPhase} only. It does not re-sort mods,
 * does not mix Fabric and Forge mods into one dependency graph, and does not know any mod id.
 */
public class DefaultBootOrchestrator implements BootOrchestrator {

	private final List<ModLoader> loaders = new ArrayList<>();
	private LoadContext context;

	@Override
	public void installLoaders(List<ModLoader> loaders) {
		this.loaders.clear();
		this.loaders.addAll(loaders);
		NextLog.info("BootOrchestrator: installed {} loader(s): {}",
				loaders.size(),
				loaders.stream().map(l -> l.ecosystem().name()).toList());
	}

	@Override
	public void discover(LoadContext context) {
		this.context = context;
		NextLog.info("BootOrchestrator: starting discovery across {} loaders", loaders.size());
		for (ModLoader loader : loaders) {
			List<ModCandidate> found = loader.discover(context);
			NextLog.info("  {}: {} candidate(s)", loader.ecosystem(), found.size());
		}
	}

	@Override
	public void resolve() {
		NextLog.info("BootOrchestrator: resolving each loader's own dependency graph independently");
		// Each loader resolves its own graph. The orchestrator does NOT pass one loader's
		// candidates into another's resolve() — that was the 0.3.0 "strong compat" bug.
		for (ModLoader loader : loaders) {
			List<ModCandidate> ownCandidates = List.of();
			loader.resolve(ownCandidates, context);
		}
	}

	@Override
	public void runLifecycle() throws Exception {
		NextLog.info("BootOrchestrator: driving lifecycle phases");
		for (LoadPhase phase : LoadPhase.values()) {
			NextLog.info("  -- phase: {}", phase);
			for (ModLoader loader : loaders) {
				loader.runPhase(phase);
			}
		}
		NextLog.info("BootOrchestrator: lifecycle complete");
	}
}
