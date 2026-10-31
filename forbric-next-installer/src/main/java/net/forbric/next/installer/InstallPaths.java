package net.forbric.next.installer;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Locates the Minecraft installation directory and the Java runtime.
 */
public final class InstallPaths {

	private InstallPaths() {}

	/**
	 * Find the default .minecraft directory for the current OS.
	 *
	 * @return the path, or {@code null} if it doesn't exist
	 */
	public static Path findMinecraftDir() {
		String home = System.getProperty("user.home");
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		Path candidate;
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			candidate = Path.of(appData != null ? appData : home + "\\AppData\\Roaming", ".minecraft");
		} else if (os.contains("mac")) {
			candidate = Path.of(home, "Library", "Application Support", "minecraft");
		} else {
			candidate = Path.of(home, ".minecraft");
		}

		return candidate.toFile().isDirectory() ? candidate : null;
	}

	/**
	 * Check that Java 17+ is available.
	 *
	 * @return the detected Java version, or -1 if unparseable
	 */
	public static int detectJavaVersion() {
		String v = System.getProperty("java.specification.version", "0");
		try {
			return Integer.parseInt(v);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Build the target versions folder for a given MC version and NeoForbric.
	 */
	public static Path versionFolder(Path minecraftDir, String mcVersion) {
		return minecraftDir.resolve("versions").resolve(mcVersion + "-neoforbric");
	}
}
