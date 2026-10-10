package net.forbric.next.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registry of available {@link VersionAdapter}s.
 *
 * <p>Boot queries this registry with the detected MC version string and gets back the
 * matching adapter, or throws if no adapter is registered.
 */
public final class VersionAdapterRegistry {

	private static final List<VersionAdapter> ADAPTERS = new ArrayList<>();

	static {
		// Skeleton adapters are registered here as they are written.
		// Functional adapters will be wired in as version support is implemented.
	}

	private VersionAdapterRegistry() {}

	public static void register(VersionAdapter adapter) {
		ADAPTERS.add(adapter);
	}

	public static Optional<VersionAdapter> find(String versionString) {
		return ADAPTERS.stream()
				.filter(a -> a.version().versionString().equals(versionString))
				.findFirst();
	}

	public static List<VersionAdapter> all() {
		return List.copyOf(ADAPTERS);
	}

	/** Initialize and register all built-in version adapters. */
	public static void initializeBuiltins() {
		register(new net.forbric.next.version.impl.V1_18_Adapter());
		register(new net.forbric.next.version.impl.V1_20_Adapter());
		register(new net.forbric.next.version.impl.V1_21_Adapter());
		register(new net.forbric.next.version.impl.V26_x_Adapter());
	}
}
