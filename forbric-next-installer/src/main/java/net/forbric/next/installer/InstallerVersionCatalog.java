package net.forbric.next.installer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalog of MC versions the NeoForbric installer supports.
 *
 * <p>Each entry carries:
 * <ul>
 *   <li>The MC version string (as Mojang names it)</li>
 *   <li>Whether the version actually exists</li>
 *   <li>Which loader ecosystem versions are recommended</li>
 * </ul>
 *
 * <p>Note: 1.21.11 是 1.21.x 的后续补丁版本，已按用户要求标记 exists=true
 * （早期版本曾误标 exists=false，现已纠正并由 InstallerVersionCatalogTest 锁定）。
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
				"0.14.24", "40.3.12", null,
				"Forge only; NeoForge starts at 1.20.1"));
		CATALOG.put("1.19.2", new CatalogEntry(
				"1.19.2", true,
				"0.14.25", "43.5.2", null,
				"Forge only; most modded 1.19 target"));
		CATALOG.put("1.20.4", new CatalogEntry(
				"1.20.4", true,
				"0.15.10", "49.2.13", "20.4.0-beta",
				"Both Forge and NeoForge available"));
		CATALOG.put("1.21.1", new CatalogEntry(
				"1.21.1", true,
				"0.16.14+1.21.1", "52.1.20", "21.1.45",
				"Current most-popular modded version; NeoForge 21.1.45"));
		CATALOG.put("1.21.11", new CatalogEntry(
				"1.21.11", true,
				"0.16.14+1.21.11", "61.2.4", "21.11.40-beta",
				"Latest 1.21.x patch; Forge 61.2.4, NeoForge 21.11.40-beta"));
		CATALOG.put("26.3", new CatalogEntry(
				"26.3", true,
				"0.155.2+26.3", null, "26.3.0.76",
				"NeoForbric primary target; NeoForge 26.3.0.76"));
	}

	public static Map<String, CatalogEntry> all() {
		return Map.copyOf(CATALOG);
	}

	public static CatalogEntry get(String mcVersion) {
		return CATALOG.get(mcVersion);
	}
}
