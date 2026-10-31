package net.neoforbric.fabricapi.language;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available {@link LanguageAdapter}s.
 *
 * <p>Boot registers the {@link DefaultJavaLanguageAdapter} under "default".
 * A mod can register additional adapters (e.g. "kotlin") by declaring them in
 * its fabric.mod.json and shipping the adapter class.
 */
public final class LanguageAdapterRegistry {

	private static final Map<String, LanguageAdapter> ADAPTERS = new ConcurrentHashMap<>();

	static {
		ADAPTERS.put(DefaultJavaLanguageAdapter.NAME, new DefaultJavaLanguageAdapter());
	}

	private LanguageAdapterRegistry() {}

	public static void register(LanguageAdapter adapter) {
		ADAPTERS.put(adapter.name(), adapter);
	}

	public static LanguageAdapter get(String name) {
		LanguageAdapter adapter = ADAPTERS.get(name);
		if (adapter == null) {
			throw new IllegalStateException("No language adapter registered for '" + name + "'. "
					+ "Install the matching language adapter mod (e.g. fabric-language-kotlin for Kotlin).");
		}
		return adapter;
	}

	public static boolean has(String name) {
		return ADAPTERS.containsKey(name);
	}
}
