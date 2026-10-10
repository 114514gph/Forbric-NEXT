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
	MINECRAFTFORGE,
	NEOFORGE
}
