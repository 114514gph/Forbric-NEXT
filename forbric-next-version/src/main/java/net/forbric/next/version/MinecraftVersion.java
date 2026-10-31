package net.forbric.next.version;

/**
 * Identifies a specific Minecraft version and its known class/mapping characteristics.
 *
 * <p>NeoForbric is not tied to a single MC version. Each supported version provides
 * a {@link VersionAdapter} that knows:
 * <ul>
 *   <li>The mapping scheme (Mojmap vs official MCP vs intermediary)</li>
 *   <li>Key class names that differ between versions (e.g. {@code World} vs {@code Level})</li>
 *   <li>Which lifecycle events exist in that version</li>
 *   <li>The minimum JRE version required</li>
 * </ul>
 */
public record MinecraftVersion(
		String versionString,   // e.g. "1.20.1", "1.21.4", "26.2"
		int major,
		int minor,
		int patch,
		String mappingScheme,   // "mojmap", "mcp", "intermediary"
		int minimumJavaVersion
) {
	public static MinecraftVersion of(String versionString, int major, int minor, int patch,
			String mappingScheme, int minimumJavaVersion) {
		return new MinecraftVersion(versionString, major, minor, patch, mappingScheme, minimumJavaVersion);
	}
}
