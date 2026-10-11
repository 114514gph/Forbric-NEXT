package net.forbric.next.installer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalog of MC versions the Forbric-NEXT installer supports.
 *
 * <p>Each entry carries:
 * <ul>
 *   <li>The MC version string (as Mojang names it)</li>
 *   <li>Whether the version actually exists</li>
 *   <li>Which loader ecosystem versions are recommended</li>
 * </ul>
 *
 * <p>Note: 1.21.11 does not exist as of writing — it is listed per user request and flagged.
 */
public final class InstallerVersionCatalog {

	private InstallerVersionCatalog() {}

	public record CatalogEntry(
			String mcVersion,
			boolean exists,
			String fabricVersion,
			String forgeVersion,
			String neoForgeVersion,
			String notes
	) {}

	private static final Map<String, CatalogEntry> CATALOG = new LinkedHashMap<>();

	static {
		CATALOG.put("1.18.2", new CatalogEntry(
				"1.18.2", true,
				"0.14.24", "40.2.17", null,
				"Forge only; NeoForge starts at 1.20.1"));
		CATALOG.put("1.19.2", new CatalogEntry(
				"1.19.2", true,
				"0.14.25", "43.4.0", null,
				"Forge only; most modded 1.19 target"));
		CATALOG.put("1.20.4", new CatalogEntry(
				"1.20.4", true,
				"0.15.10", "49.4.0", "20.4.0-beta",
				"Both Forge and NeoForge available"));
		CATALOG.put("1.21.1", new CatalogEntry(
				"1.21.1", true,
				"0.16.5", "52.0.39", "21.1.21",
				"Current most-popular modded version"));
		CATALOG.put("1.21.11", new CatalogEntry(
				"1.21.11", false,
				null, null, null,
				"WARNING: This MC version does not exist as of 2026-10. Listed per user request."));
		CATALOG.put("26.3", new CatalogEntry(
				"26.3", true,
				"0.155.2+26.3", null, "26.3.x",
				"Forbric-NEXT primary target; NeoForge primary Forge lineage"));
	}

	public static Map<String, CatalogEntry> all() {
		return Map.copyOf(CATALOG);
	}

	public static CatalogEntry get(String mcVersion) {
		return CATALOG.get(mcVersion);
	}
}
