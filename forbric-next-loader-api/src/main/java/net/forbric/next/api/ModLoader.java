package net.forbric.next.api;

import java.util.List;

/**
 * The service-provider interface every ecosystem loader implements.
 *
 * <h2>Design contract</h2>
 * <p>A {@code ModLoader} owns <em>everything inside</em> its ecosystem:
 * <ul>
 *   <li>discovering mod jars and parsing metadata (fabric.mod.json / mods.toml / neoforge.mods.toml)</li>
 *   <li>resolving the dependency graph and choosing which version of each mod to load</li>
 *   <li>ordering mods the way the <em>native</em> loader orders them (Fabric by mod-id lexicographic;
 *       Forge/NeoForge by their own ModSorter using explicit BEFORE/AFTER edges only)</li>
 *   <li>running entrypoints in the native group order (preLaunch → main → client/server for Fabric;
 *       Construct → CommonSetup → SidedSetup → Registration → IMC → LoadComplete for Forge)</li>
 * </ul>
 *
 * <p>The boot module (Forbric-NEXT's orchestrator) decides the <em>between-ecosystem</em> sequence:
 * which loader scans first, which lifecycle window runs which group, and when handoff happens.
 * It must never call into a loader's internal mod list to re-sort it.
 */
public interface ModLoader {

	/** Which ecosystem this loader serves. */
	Ecosystem ecosystem();

	/**
	 * Discover mod jars on disk and parse them into candidates.
	 * Called once at boot, before any mod class loads.
	 */
	List<ModCandidate> discover(LoadContext context);

	/**
	 * Resolve the dependency graph and select the mod set to load.
	 *
	 * <p>This is where Fabric's ModSolver / Forge's ModSorter would run natively.
	 * Implementations must use the native ordering semantics for their ecosystem:
	 * <ul>
	 *   <li>Fabric: topological selection, then order by mod-id lexicographic.</li>
	 *   <li>Forge/NeoForge: order by explicit BEFORE/AFTER edges only — mandatory {@code depends}
	 *       does <em>not</em> create an ordering edge (it is a presence requirement, not a sequence).</li>
	 * </ul>
	 */
	void resolve(List<ModCandidate> candidates, LoadContext context);

	/**
	 * Run a lifecycle phase. The boot module calls this in the correct between-ecosystem order.
	 *
	 * @param phase the phase to run
	 * @throws Exception if a mod in this ecosystem fails the phase
	 */
	void runPhase(LoadPhase phase) throws Exception;

	/** Called after all phases complete, for cleanup/reporting. */
	default void onShutdown() {}
}
