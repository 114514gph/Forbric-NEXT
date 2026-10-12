package net.forbric.next.api;

/**
 * The mod ecosystems Forbric-NEXT can co-load.
 *
 * <p>Each ecosystem has its own discovery, dependency graph, entrypoint ordering, and lifecycle.
 * Forbric-NEXT's boot module decides <em>between</em> ecosystems (who scans first, which window
 * runs which), but never re-sorts a mod list that belongs to another ecosystem.
 */
public enum Ecosystem {
	FABRIC,
	/**
	 * Quilt —— Fabric 的分支生态系。
	 *
	 * <p>Quilt mod 通常同时带 {@code fabric.mod.json} 和扩展元数据 {@code quilt.mod.json}。
	 * 其 entrypoint / mixin / JiJ 机制与 Fabric 高度兼容，因此 Quilt mod 在 catalog 里单独标记为
	 * {@code QUILT}，但加载路径复用 Fabric 的那一套（见 forbric-next-fabric 的 QuiltModDetector）。
	 */
	QUILT,
	MINECRAFTFORGE,
	NEOFORGE
}
