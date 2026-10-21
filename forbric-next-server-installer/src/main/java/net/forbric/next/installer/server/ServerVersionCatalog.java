package net.forbric.next.installer.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalog of MC versions the Forbric-NEXT server installer supports.
 * Mirrors the client installer's catalog — kept standalone so the server installer
 * doesn't need to depend on the GUI module.
 */
public final class ServerVersionCatalog {

	private ServerVersionCatalog() {}

	public record Entry(
			String mcVersion,
			boolean exists,
			String fabricVersion,
			String forgeVersion,
			String neoForgeVersion,
			String notes
	) {}

	private static final Map<String, Entry> CATALOG = new LinkedHashMap<>();

	static {
		CATALOG.put("1.18.2", new Entry("1.18.2", true, "0.14.24", "40.3.12", null,
				"Forge only; NeoForge starts at 1.20.1"));
		CATALOG.put("1.19.2", new Entry("1.19.2", true, "0.14.25", "43.5.2", null,
				"Forge only; most modded 1.19 target"));
		CATALOG.put("1.20.4", new Entry("1.20.4", true, "0.15.10", "49.2.13", "20.4.0-beta",
				"Both Forge and NeoForge available"));
		CATALOG.put("1.21.1", new Entry("1.21.1", true, "0.16.14+1.21.1", "52.1.20", "21.1.45",
				"Most-popular modded version; NeoForge 21.1.45"));
		CATALOG.put("1.21.11", new Entry("1.21.11", true, "0.16.14+1.21.11", "61.2.4", "21.11.40-beta",
				"Latest 1.21.x patch; Forge 61.2.1, NeoForge 21.11.40-beta"));
		CATALOG.put("26.3", new Entry("26.3", true, "0.155.2+26.3", null, "26.3.0.76",
				"Primary target; NeoForge 26.3.0.76"));
	}

	public static Map<String, Entry> all() { return Map.copyOf(CATALOG); }
	public static Entry get(String v) { return CATALOG.get(v); }
}
