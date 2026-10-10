package net.forbric.next.api;

import java.util.function.Consumer;

/**
 * Unified event-bus abstraction across ecosystems.
 *
 * <p>Forbric 0.3.0 scattered hundreds of event hook points through a 3800-line
 * {@code ForbricMergedBaseCompatTransformer}, with per-mod branches for Carpet, Create, C2ME, etc.
 * Forbric-NEXT exposes this as a service interface: each ecosystem adapter (FabricEventAdapter,
 * ForgeEventAdapter, NeoEventAdapter) posts to its native bus, and cross-ecosystem bridges
 * implement this interface to forward events.
 *
 * <p>There are no per-mod hooks here. A mod that needs a specific event registers a listener
 * through its own ecosystem's API; Forbric-NEXT only provides the bus plumbing.
 *
 * @param <T> the event type
 */
public interface EventBus<T> {

	/** Register a listener. */
	void add(Consumer<T> listener);

	/** Post an event to all listeners. */
	void post(T event);

	/** Remove all listeners. */
	void clear();
}
