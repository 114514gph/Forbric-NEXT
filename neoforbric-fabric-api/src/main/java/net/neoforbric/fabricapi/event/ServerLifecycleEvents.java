package net.neoforbric.fabricapi.event;

import java.util.function.Consumer;

/**
 * Server lifecycle events, mirroring Fabric API's {@code ServerLifecycleEvents}.
 *
 * <p>A mod registers on these to run code when the dedicated server starts/stops.
 * NeoForbric posts to these from the boot orchestrator's GAME_STARTED / shutdown phases.
 *
 * <p><b>事件上下文用具体 record，不用 Proxy：</b>每个事件都带一个具体的 {@code XxxEvent}
 * record（如 {@link ServerStartedEvent}），回调签名是 {@code Consumer<XxxEvent>} 而不是
 * 无参 {@code Runnable}。这样 mod 既能拿到上下文，又不会踩 Proxy 的
 * equals() / instanceof / 新方法 UnsupportedOperationException 三个坑。
 */
public final class ServerLifecycleEvents {

	private ServerLifecycleEvents() {}

	/** Fired before the server begins ticking. */
	public static final Event<Consumer<ServerStartingEvent>> SERVER_STARTING = Event.ofConsumer();

	/** Fired when the server has loaded all levels and is accepting connections. */
	public static final Event<Consumer<ServerStartedEvent>> SERVER_STARTED = Event.ofConsumer();

	/** Fired when the server begins stopping (before save). */
	public static final Event<Consumer<ServerStoppingEvent>> SERVER_STOPPING = Event.ofConsumer();

	/** Fired after the server has fully stopped. */
	public static final Event<Consumer<ServerStoppedEvent>> SERVER_STOPPED = Event.ofConsumer();
}
