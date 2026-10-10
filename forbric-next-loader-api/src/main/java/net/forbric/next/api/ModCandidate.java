package net.forbric.next.api;

import java.nio.file.Path;
import java.util.List;

/**
 * A mod discovered on disk, before any ecosystem-specific resolution runs.
 *
 * <p>This is the neutral descriptor that flows between discovery and the per-ecosystem loaders.
 * Each loader reads the fields it cares about (Fabric reads {@code fabricModJson}, Forge reads
 * {@code modsToml}, etc.) and builds its own internal representation.
 *
 * @param id         the mod id (may be {@code null} until the ecosystem-specific parser runs)
 * @param version    the mod version string
 * @param ecosystem  which ecosystem this mod belongs to
 * @param jarPath    path to the mod jar on disk
 * @param parentJars nested JiJ jars discovered inside this mod
 */
public record ModCandidate(
		String id,
		String version,
		Ecosystem ecosystem,
		Path jarPath,
		List<Path> parentJars
) {
}
