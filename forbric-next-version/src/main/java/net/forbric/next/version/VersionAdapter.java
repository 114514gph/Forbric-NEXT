package net.forbric.next.version;

/**
 * SPI for per-Minecraft-version adapters.
 *
 * <p>Each supported MC version registers one adapter. The boot layer picks the adapter
 * matching the game jar's version string, then delegates version-specific lookups
 * (class names, event constants, registry paths) to it.
 *
 * <p>This replaces Forbric 0.3.0's hardcoded "MC 26.2 only" assumptions: class names
 * like {@code net.minecraft.world.level.block.Block} or event constants like
 * {@code net.minecraft.server.MinecraftServer} differ between 1.18, 1.20, 1.21, and 26.x.
 *
 * <p><b>How to fill in a new adapter:</b> do not guess class names.
 * <ul>
 *   <li>Use <a href="https://linkie.shedaniel.dev">Linkie</a> (the Fabric team's mappings lookup)
 *       to find class/method/field names across versions and mapping schemes.</li>
 *   <li>Since 1.19.4, Mojang publishes <b>official mappings</b> (Mojmap) — the game jar is no
 *       longer fully obfuscated. Decompile with Vineflower/Florio (or use Mojang's published
 *       mappings CSV directly) to see exact names for that version.</li>
 *   <li>For pre-1.19.4 versions, you need MCP/Searge mappings and intermediary (Fabric's
 *       deobfuscation layer). Cross-check all three before hardcoding.</li>
 * </ul>
 */
public interface VersionAdapter {

	/** The MC version this adapter serves. */
	MinecraftVersion version();

	/**
	 * Resolve a version-relative class name.
	 *
	 * @param key a logical key like "block", "item", "server", "client"
	 * @return the fully-qualified class name in this MC version's mapping
	 */
	String resolveClassName(String key);

	/**
	 * Whether this version has a given lifecycle event.
	 *
	 * @param eventKey e.g. "server_starting", "client_tick", "registry_freeze"
	 */
	boolean hasEvent(String eventKey);

	/**
	 * Whether this adapter is fully implemented or just a skeleton placeholder.
	 */
	boolean isFunctional();
}
