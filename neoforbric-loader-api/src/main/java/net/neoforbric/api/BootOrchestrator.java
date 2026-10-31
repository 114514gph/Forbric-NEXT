package net.neoforbric.api;

import java.util.List;

/**
 * The boot orchestrator. This is the <em>only</em> place where all three loaders meet.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Discover mods by calling each {@link ModLoader#discover} in a defined order.</li>
 *   <li>Resolve each loader's dependency graph independently (no cross-ecosystem topological mix).</li>
 *   <li>Drive {@link LoadPhase} across loaders in the canonical between-ecosystem order.</li>
 *   <li>Expose a read-only {@link #globalCatalog()} aggregating what <em>every</em> loader found,
 *       so entrypoint lookups are visible across loaders (a library mod discovered by one loader
 *       can still contribute entrypoints another mod needs).</li>
 * </ol>
 *
 * <p>Non-responsibilities (explicitly, to prevent regression to 0.3.0's "strong compat" mode):
 * <ul>
 *   <li>Never re-sort a loader's internal mod list.</li>
 *   <li>Never call a loader's entrypoint driver directly — only {@link ModLoader#runPhase}.</li>
 *   <li>Never inject per-mod special cases (no {@code if modId.equals("carpet")} anywhere).</li>
 *   <li>The global catalog is <em>read-only visibility</em>: it does NOT feed back into any
 *       loader's own resolve()/ordering. Each loader still resolves only the candidates it
 *       discovered itself.</li>
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

	/**
	 * 所有 loader discover 出来的 mod 汇总成的全局目录（只读）。
	 *
	 * <p>用途：entrypoint 查找时全局搜索，不局限于当前 loader。一个库 mod（如 SpectreLib）
	 * 即便被 NeoForge loader 发现，它声明的 entrypoint 仍要对别的 mod 可见。
	 * 这只是"可见性"汇总——绝不参与任何 loader 内部的排序 / 依赖图。
	 *
	 * @return discover 之后的全局 mod 列表；discover 之前返回空列表
	 */
	List<ModCandidate> globalCatalog();
}
