package net.forbric.next.fabricapi.interop;

import java.nio.file.Path;
import java.util.function.Consumer;

import net.forbric.next.fabricapi.event.ClientLifecycleEvents;
import net.forbric.next.fabricapi.event.ClientStartedEvent;
import net.forbric.next.fabricapi.event.ClientStoppingEvent;
import net.forbric.next.fabricapi.event.Event;
import net.forbric.next.fabricapi.event.ServerLifecycleEvents;
import net.forbric.next.fabricapi.event.ServerStartedEvent;
import net.forbric.next.fabricapi.event.ServerStartingEvent;
import net.forbric.next.fabricapi.event.ServerStoppedEvent;
import net.forbric.next.fabricapi.event.ServerStoppingEvent;

/**
 * 通用接缝层（Generic Interop）。
 *
 * <p><b>设计动机：</b>0.3.0 里 Forbric 为每个 mod（Carpet / Create / C2ME ...）打一个补丁分支，
 * 加一个 {@code if modId.equals(...)}。NEXT 反其道而行——暴露一组与具体 mod 无关的通用回调，
 * 任何 mod / 适配层都通过这组接口注册钩子，游戏事件发生时统一触发。
 *
 * <p><b>事件上下文用具体 record，不用 Proxy：</b>生命周期回调从无参 {@link Runnable} 升级为
 * {@code Consumer<XxxEvent>}，事件对象是具体 record（{@link ServerStartedEvent} 等）。
 * 这样 mod 既能拿到上下文（游戏目录 / 窗口标题），又不会踩 Proxy 的坑——
 * equals() 正确、{@code instanceof} 可用、以后给事件加字段也不会抛 UnsupportedOperationException。
 * 方块 / 物品注册事件不携带上下文，仍是无参 Runnable。
 *
 * <p>没有任何 per-mod 分支，没有硬编码 mod id。
 */
public final class GenericEventBridge {

	private GenericEventBridge() {}

	/** 方块注册阶段事件（注册窗口 unfreeze 时触发）。无上下文，保持 Runnable。 */
	public static final Event<Runnable> REGISTER_BLOCKS = Event.ofRunnable();
	/** 物品注册阶段事件。无上下文，保持 Runnable。 */
	public static final Event<Runnable> REGISTER_ITEMS = Event.ofRunnable();

	// ---- 客户端生命周期（委托到 ClientLifecycleEvents）----

	/** 客户端启动完成（主窗口已显示）。 */
	public static void onClientStarted(Consumer<ClientStartedEvent> hook) {
		ClientLifecycleEvents.CLIENT_STARTED.register(hook);
	}

	/** 客户端即将关闭。 */
	public static void onClientStopping(Consumer<ClientStoppingEvent> hook) {
		ClientLifecycleEvents.CLIENT_STOPPING.register(hook);
	}

	// ---- 服务端生命周期（委托到 ServerLifecycleEvents）----

	/** 服务端启动完成（已加载所有 dimension、开始接受连接）。 */
	public static void onServerStarted(Consumer<ServerStartedEvent> hook) {
		ServerLifecycleEvents.SERVER_STARTED.register(hook);
	}

	/** 服务端即将停止（保存之前）。 */
	public static void onServerStopping(Consumer<ServerStoppingEvent> hook) {
		ServerLifecycleEvents.SERVER_STOPPING.register(hook);
	}

	/** 服务端完全停止。 */
	public static void onServerStopped(Consumer<ServerStoppedEvent> hook) {
		ServerLifecycleEvents.SERVER_STOPPED.register(hook);
	}

	// ---- 注册阶段 ----

	/** 方块注册阶段回调。 */
	public static void onRegisterBlocks(Runnable hook) {
		REGISTER_BLOCKS.register(hook);
	}

	/** 物品注册阶段回调。 */
	public static void onRegisterItems(Runnable hook) {
		REGISTER_ITEMS.register(hook);
	}

	// ---- 触发点（由 boot / 游戏侧调用，mod 不直接调）----

	/** 触发所有方块注册回调。 */
	public static void fireRegisterBlocks() {
		REGISTER_BLOCKS.invoker().run();
	}

	/** 触发所有物品注册回调。 */
	public static void fireRegisterItems() {
		REGISTER_ITEMS.invoker().run();
	}

	/** 触发所有"服务端即将开始 tick"回调。 */
	public static void fireServerStarting(Path serverDirectory) {
		ServerLifecycleEvents.SERVER_STARTING.invoker().accept(new ServerStartingEvent(serverDirectory));
	}

	/** 触发所有"服务端启动完成"回调，传入服务端目录作为上下文。 */
	public static void fireServerStarted(Path serverDirectory) {
		ServerLifecycleEvents.SERVER_STARTED.invoker().accept(new ServerStartedEvent(serverDirectory));
	}

	/** 触发所有"服务端即将停止"回调，传入服务端目录作为上下文。 */
	public static void fireServerStopping(Path serverDirectory) {
		ServerLifecycleEvents.SERVER_STOPPING.invoker().accept(new ServerStoppingEvent(serverDirectory));
	}

	/** 触发所有"服务端完全停止"回调，传入服务端目录作为上下文。 */
	public static void fireServerStopped(Path serverDirectory) {
		ServerLifecycleEvents.SERVER_STOPPED.invoker().accept(new ServerStoppedEvent(serverDirectory));
	}

	/** 触发所有"客户端启动完成"回调，传入窗口标题作为上下文。 */
	public static void fireClientStarted(String windowTitle) {
		ClientLifecycleEvents.CLIENT_STARTED.invoker().accept(new ClientStartedEvent(windowTitle));
	}

	/** 触发所有"客户端即将关闭"回调。 */
	public static void fireClientStopping() {
		ClientLifecycleEvents.CLIENT_STOPPING.invoker().accept(new ClientStoppingEvent());
	}
}
