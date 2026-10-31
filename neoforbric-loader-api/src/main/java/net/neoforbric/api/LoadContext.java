package net.neoforbric.api;

import java.nio.file.Path;

/**
 * The immutable context handed to every {@link ModLoader} at startup.
 *
 * @param gameDir      the running game directory
 * @param modsDir      the directory to scan for mod jars
 * @param gameJarPath  the patched Minecraft jar the loader will transform/load
 * @param environment  client or server
 */
public record LoadContext(
		Path gameDir,
		Path modsDir,
		Path gameJarPath,
		Environment environment
) {
	public enum Environment { CLIENT, SERVER }
}
