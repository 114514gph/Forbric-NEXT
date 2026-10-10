package net.forbric.next.fabricapi.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Minimal event bus mimicking Fabric API's event model.
 *
 * <p>Fabric API events are each a singleton object mods register listeners on, and the
 * game-side code invokes via {@code invoker()}. Forbric-NEXT provides this minimal
 * implementation so a mod calling {@code ServerLifecycleEvents.SERVER_STARTED.register(hook)}
 * works without the user installing the full fabric-api jar.
 *
 * <p>This is intentionally tiny: no phases, no cancellable events, no event groups.
 * It covers the 90% case (lifecycle + simple callbacks). Richer events can be added later.
 *
 * @param <T> the listener type
 */
public final class Event<T> {

	private final List<T> listeners = new CopyOnWriteArrayList<>();
	private final Function<Iterable<T>, T> invokerFactory;

	private Event(Function<Iterable<T>, T> invokerFactory) {
		this.invokerFactory = invokerFactory;
	}

	/** Create an event where the invoker is a single combined listener. */
	public static <T> Event<T> ofCombiner(Function<Iterable<T>, T> combiner) {
		return new Event<>(combiner);
	}

	/** Create a simple runnable event (zero-arg hooks). */
	public static Event<Runnable> ofRunnable() {
		return new Event<>(listeners -> () -> {
			for (Runnable r : listeners) r.run();
		});
	}

	/** Register a listener. */
	public void register(T listener) {
		listeners.add(listener);
	}

	/** The combined invoker that fans out to all registered listeners. */
	public T invoker() {
		return invokerFactory.apply(listeners);
	}

	/** Number of registered listeners (diagnostics). */
	public int listenerCount() {
		return listeners.size();
	}

	/** Clear all listeners (for shutdown). */
	public void clear() {
		listeners.clear();
	}
}
