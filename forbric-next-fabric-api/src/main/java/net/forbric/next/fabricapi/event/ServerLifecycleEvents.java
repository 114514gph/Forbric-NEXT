package net.forbric.next.fabricapi.event;

/**
 * Server lifecycle events, mirroring Fabric API's {@code ServerLifecycleEvents}.
 *
 * <p>A mod registers on these to run code when the dedicated server starts/stops.
 * Forbric-NEXT posts to these from the boot orchestrator's GAME_STARTED / shutdown phases.
 */
public final class ServerLifecycleEvents {

	private ServerLifecycleEvents() {}

	/** Fired before the server begins ticking. */
	public static final Event<Runnable> SERVER_STARTING = Event.ofRunnable();

	/** Fired when the server has loaded all levels and is accepting connections. */
	public static final Event<Runnable> SERVER_STARTED = Event.ofRunnable();

	/** Fired when the server begins stopping (before save). */
	public static final Event<Runnable> SERVER_STOPPING = Event.ofRunnable();

	/** Fired after the server has fully stopped. */
	public static final Event<Runnable> SERVER_STOPPED = Event.ofRunnable();
}
