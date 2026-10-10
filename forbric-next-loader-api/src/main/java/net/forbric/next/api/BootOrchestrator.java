package net.forbric.next.api;

import java.util.List;

/**
 * The boot orchestrator. This is the <em>only</em> place where all three loaders meet.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Discover mods by calling each {@link ModLoader#discover} in a defined order.</li>
 *   <li>Resolve each loader's dependency graph independently (no cross-ecosystem topological mix).</li>
 *   <li>Drive {@link LoadPhase} across loaders in the canonical between-ecosystem order.</li>
 * </ol>
 *
 * <p>Non-responsibilities (explicitly, to prevent regression to 0.3.0's "strong compat" mode):
 * <ul>
 *   <li>Never re-sort a loader's internal mod list.</li>
 *   <li>Never call a loader's entrypoint driver directly — only {@link ModLoader#runPhase}.</li>
 *   <li>Never inject per-mod special cases (no {@code if modId.equals("carpet")} anywhere).</li>
 * </ul>
 */
public interface BootOrchestrator {

	/**
	 * Install the loaders to run, in between-ecosystem scan order.
	 * Typically: NeoForge first, then MinecraftForge, then Fabric (or configured order).
	 */
	void installLoaders(List<ModLoader> loaders);

	/** Discover mods across all installed loaders. */
	void discover(LoadContext context);

	/** Resolve each loader's own dependency graph independently. */
	void resolve();

	/** Drive all loaders through the phase sequence. */
	void runLifecycle() throws Exception;
}
