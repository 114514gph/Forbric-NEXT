package net.forbric.next.fabricapi.event;

/**
 * Client lifecycle events, mirroring Fabric API's {@code ClientLifecycleEvents}.
 */
public final class ClientLifecycleEvents {

	private ClientLifecycleEvents() {}

	/** Fired when the Minecraft client has started and the main window is shown. */
	public static final Event<Runnable> CLIENT_STARTED = Event.ofRunnable();

	/** Fired when the client is stopping. */
	public static final Event<Runnable> CLIENT_STOPPING = Event.ofRunnable();
}
