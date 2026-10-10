package net.forbric.next.api;

/**
 * How mod classes are delegated between the boot classloader, the game classloader, and mod jars.
 *
 * <p>Per-ecosystem loaders provide their own policy. The boot module uses it when constructing
 * the isolating classloader. This replaces Forbric 0.3.0's hand-written {@code DelegationPolicy}
 * string-prefix table with a first-class interface.
 */
public interface ClassLoadingPolicy {

	/**
	 * Decide whether a class name should be loaded by the parent (game/boot) classloader
	 * or defined by the mod classloader.
	 */
	Delegate delegate(String className);

	enum Delegate {
		/** Always use the parent loader (JDK classes, loader API surface). */
		PARENT_FIRST,
		/** Mod classloader defines this class if present in a mod jar. */
		CHILD_FIRST,
		/** Game classes live here (net.minecraft.*). */
		GAME
	}
}
