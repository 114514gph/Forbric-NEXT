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
		CATALOG.put("1.18.2", new Entry("1.18.2", true, "0.14.24", "40.2.17", null,
				"Forge only; NeoForge starts at 1.20.1"));
		CATALOG.put("1.19.2", new Entry("1.19.2", true, "0.14.25", "43.4.0", null,
				"Forge only; most modded 1.19 target"));
		CATALOG.put("1.20.4", new Entry("1.20.4", true, "0.15.10", "49.4.0", "20.4.0-beta",
				"Both Forge and NeoForge available"));
		CATALOG.put("1.21.1", new Entry("1.21.1", true, "0.16.5", "52.0.39", "21.1.21",
				"Current most-popular modded version"));
		CATALOG.put("1.21.11", new Entry("1.21.11", true, "0.16.14+1.21.11", "52.x", "21.11.x",
				"Latest 1.21.x patch (confirmed via Mojang manifest)"));
		CATALOG.put("26.3", new Entry("26.3", true, "0.155.2+26.3", null, "26.3.x",
				"Forbric-NEXT primary target; NeoForge primary Forge lineage"));
	}

	public static Map<String, Entry> all() { return Map.copyOf(CATALOG); }
	public static Entry get(String v) { return CATALOG.get(v); }
}
