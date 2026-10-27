package net.forbric.next.fabricapi.event;

import java.util.function.Consumer;

/**
 * Client lifecycle events, mirroring Fabric API's {@code ClientLifecycleEvents}.
 *
 * <p><b>事件上下文用具体 record，不用 Proxy：</b>回调是 {@code Consumer<XxxEvent>}，
 * 事件对象本身是具体 record（{@link ClientStartedEvent} / {@link ClientStoppingEvent}）。
 */
public final class ClientLifecycleEvents {

	private ClientLifecycleEvents() {}

	/** Fired when the Minecraft client has started and the main window is shown. */
	public static final Event<Consumer<ClientStartedEvent>> CLIENT_STARTED = Event.ofConsumer();

	/** Fired when the client is stopping. */
	public static final Event<Consumer<ClientStoppingEvent>> CLIENT_STOPPING = Event.ofConsumer();
}
