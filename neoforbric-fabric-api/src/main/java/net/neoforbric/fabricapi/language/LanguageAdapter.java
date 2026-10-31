package net.neoforbric.fabricapi.language;

/**
 * SPI for language adapters that instantiate mod entrypoint classes.
 *
 * <p>Fabric mods declare entrypoint adapters in {@code fabric.mod.json}:
 * <pre>{@code
 * "languageAdapters": { "kotlin": "net.fabricmc.language.kotlin.KotlinAdapter" }
 * }</pre>
 *
 * <p>The default "default" adapter instantiates plain Java classes via
 * {@code Class.forName(name).getDeclaredConstructor().newInstance()}. Kotlin/Scala/other
 * JVM languages provide their own adapter that knows how to construct the entrypoint
 * (e.g. calling a Kotlin object's INSTANCE field).
 *
 * <p>NeoForbric ships the Java adapter out of the box. Kotlin and Scala adapters are
 * pluggable: a mod jar can declare its own, or the user can install fabric-language-kotlin.
 */
public interface LanguageAdapter {

	/**
	 * Create an entrypoint instance from a declaration string.
	 *
	 * @param value the value from fabric.mod.json (a class name, or {@code Class::staticField},
	 *              or {@code Class::staticMethod})
	 * @param type  the expected interface type
	 * @param loader the mod's classloader
	 * @return the constructed instance
	 */
	<T> T create(String value, Class<T> type, ClassLoader loader) throws Exception;

	/** The adapter name as declared in fabric.mod.json's {@code languageAdapters}. */
	String name();
}
